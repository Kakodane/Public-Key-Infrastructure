package com.ib.service.crypto;

import com.ib.crypto.CryptoOperationException;
import com.ib.crypto.MasterKeyProvider;
import com.ib.crypto.OrganizationKeyService;
import com.ib.crypto.TotpSecretCipher;
import com.ib.domain.entity.MasterKey;
import com.ib.domain.entity.MasterKeyRotationJob;
import com.ib.domain.entity.Organization;
import com.ib.domain.entity.User;
import com.ib.domain.enums.MasterKeyRotationJobStatus;
import com.ib.domain.repository.MasterKeyRepository;
import com.ib.domain.repository.MasterKeyRotationJobRepository;
import com.ib.domain.repository.OrganizationRepository;
import com.ib.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.crypto.KeyGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
public class MasterKeyRotationService {

	private static final Logger log = LoggerFactory.getLogger(MasterKeyRotationService.class);
	private static final String AES_ALGORITHM = "AES";
	private static final int MASTER_KEY_BITS = 256;
	private static final int MASTER_KEY_BYTES = 32;

	private final MasterKeyRotationService self;
	private final MasterKeyRepository masterKeyRepository;
	private final MasterKeyRotationJobRepository rotationJobRepository;
	private final OrganizationRepository organizationRepository;
	private final UserRepository userRepository;
	private final MasterKeyProvider masterKeyProvider;
	private final OrganizationKeyService organizationKeyService;
	private final TotpSecretCipher totpSecretCipher;
	private final int batchSize;

	public MasterKeyRotationService(@Lazy MasterKeyRotationService self,
	                                MasterKeyRepository masterKeyRepository,
	                                MasterKeyRotationJobRepository rotationJobRepository,
	                                OrganizationRepository organizationRepository,
	                                UserRepository userRepository,
	                                MasterKeyProvider masterKeyProvider,
	                                OrganizationKeyService organizationKeyService,
	                                TotpSecretCipher totpSecretCipher,
	                                @Value("${crypto.master-key-rotation-batch-size:50}") int batchSize) {
		this.self = self;
		this.masterKeyRepository = masterKeyRepository;
		this.rotationJobRepository = rotationJobRepository;
		this.organizationRepository = organizationRepository;
		this.userRepository = userRepository;
		this.masterKeyProvider = masterKeyProvider;
		this.organizationKeyService = organizationKeyService;
		this.totpSecretCipher = totpSecretCipher;
		this.batchSize = batchSize;
	}


	public MasterKeyRotationJob rotate() {
		return rotationJobRepository.findFirstByStatus(MasterKeyRotationJobStatus.RUNNING)
				.orElseGet(self::startRotation);
	}

	public MasterKeyRotationJob findRunningJob() {
		return rotationJobRepository.findFirstByStatus(MasterKeyRotationJobStatus.RUNNING)
				.orElse(null);
	}

	@Transactional
	MasterKeyRotationJob startRotation() {
		var existingJob = rotationJobRepository.findFirstByStatus(MasterKeyRotationJobStatus.RUNNING);
		if (existingJob.isPresent()) {
			return existingJob.get();
		}

		MasterKey currentKey = masterKeyRepository.findByActiveTrue()
				.orElseThrow(() -> new IllegalStateException("No active master key configured."));
		int fromVersion = currentKey.getVersion();
		int toVersion = fromVersion + 1;

		byte[] newKeyMaterial = generateMasterKeyMaterial();
		try {
			Instant now = Instant.now();
			currentKey.setActive(false);
			masterKeyRepository.save(currentKey);

			masterKeyRepository.save(MasterKey.builder()
					.version(toVersion)
					.keyMaterial(Arrays.copyOf(newKeyMaterial, newKeyMaterial.length))
					.active(true)
					.createdAt(now)
					.build());

			masterKeyProvider.evictAll();

			int totalOrgs = (int) organizationRepository.countNeedingMasterKeyRotation(toVersion);
			int totalUsers = (int) userRepository.countNeedingTotpMasterKeyRotation(toVersion);

			MasterKeyRotationJob job = rotationJobRepository.save(MasterKeyRotationJob.builder()
					.status(MasterKeyRotationJobStatus.RUNNING)
					.fromVersion(fromVersion)
					.toVersion(toVersion)
					.totalOrgs(totalOrgs)
					.totalUsers(totalUsers)
					.startedAt(now)
					.build());

			Long jobId = job.getId();
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					self.runMigrationAsync(jobId, fromVersion, toVersion);
				}
			});

			return job;
		} finally {
			Arrays.fill(newKeyMaterial, (byte) 0);
		}
	}

	@Async
	public void runMigrationAsync(Long jobId, int fromVersion, int toVersion) {
		try {
			migrateOrganizations(jobId, fromVersion, toVersion);
			migrateUsers(jobId, fromVersion, toVersion);
			self.completeRotation(jobId, fromVersion);
		} catch (Exception ex) {
			log.error("Master key rotation job {} failed", jobId, ex);
			self.failRotation(jobId, ex);
		}
	}

	private void migrateOrganizations(Long jobId, int fromVersion, int toVersion) {
		int processed;
		do {
			processed = self.processOrganizationBatch(jobId, fromVersion, toVersion);
		} while (processed > 0);
	}

	private void migrateUsers(Long jobId, int fromVersion, int toVersion) {
		int processed;
		do {
			processed = self.processUserBatch(jobId, fromVersion, toVersion);
		} while (processed > 0);
	}

	@Transactional
	int processOrganizationBatch(Long jobId, int fromVersion, int toVersion) {
		List<Organization> batch = organizationRepository.findNeedingMasterKeyRotation(
				toVersion, PageRequest.of(0, batchSize));
		if (batch.isEmpty()) {
			return 0;
		}
		for (Organization organization : batch) {
			organizationKeyService.rewrapOrgKey(organization, fromVersion, toVersion);
		}
		organizationRepository.saveAll(batch);
		incrementOrgsProcessed(jobId, batch.size());
		return batch.size();
	}

	@Transactional
	int processUserBatch(Long jobId, int fromVersion, int toVersion) {
		List<User> batch = userRepository.findNeedingTotpMasterKeyRotation(
				toVersion, PageRequest.of(0, batchSize));
		if (batch.isEmpty()) {
			return 0;
		}
		for (User user : batch) {
			totpSecretCipher.rewrapTotpSecret(user, fromVersion, toVersion);
		}
		userRepository.saveAll(batch);
		incrementUsersProcessed(jobId, batch.size());
		return batch.size();
	}

	@Transactional
	void completeRotation(Long jobId, int retiredVersion) {
		MasterKeyRotationJob job = rotationJobRepository.findById(jobId)
				.orElseThrow(() -> new IllegalStateException("Rotation job not found: " + jobId));
		job.setStatus(MasterKeyRotationJobStatus.COMPLETED);
		job.setCompletedAt(Instant.now());
		rotationJobRepository.save(job);

		masterKeyRepository.findByVersion(retiredVersion).ifPresent(oldKey -> {
			if (oldKey.getRetiredAt() == null) {
				oldKey.setRetiredAt(Instant.now());
				masterKeyRepository.save(oldKey);
			}
		});
	}

	@Transactional
	void failRotation(Long jobId, Exception cause) {
		MasterKeyRotationJob job = rotationJobRepository.findById(jobId)
				.orElseThrow(() -> new IllegalStateException("Rotation job not found: " + jobId));
		job.setStatus(MasterKeyRotationJobStatus.FAILED);
		job.setCompletedAt(Instant.now());
		job.setErrorMessage(cause.getMessage());
		rotationJobRepository.save(job);
	}

	private void incrementOrgsProcessed(Long jobId, int count) {
		rotationJobRepository.findById(jobId).ifPresent(job -> {
			job.setOrgsProcessed(job.getOrgsProcessed() + count);
			rotationJobRepository.save(job);
		});
	}

	private void incrementUsersProcessed(Long jobId, int count) {
		rotationJobRepository.findById(jobId).ifPresent(job -> {
			job.setUsersProcessed(job.getUsersProcessed() + count);
			rotationJobRepository.save(job);
		});
	}

	private static byte[] generateMasterKeyMaterial() {
		try {
			KeyGenerator keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM);
			keyGenerator.init(MASTER_KEY_BITS);
			byte[] keyMaterial = keyGenerator.generateKey().getEncoded();
			if (keyMaterial.length != MASTER_KEY_BYTES) {
				throw new IllegalStateException("Generated master key has unexpected length.");
			}
			return keyMaterial;
		} catch (NoSuchAlgorithmException ex) {
			throw new CryptoOperationException("Master key generation failed.", ex);
		}
	}
}
