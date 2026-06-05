package com.ib.crypto;

import com.ib.domain.entity.MasterKey;
import com.ib.domain.repository.MasterKeyRepository;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MasterKeyProvider {

	private static final int MASTER_KEY_BYTES = 32;
	private static final String AES_ALGORITHM = "AES";

	private final MasterKeyRepository masterKeyRepository;
	private final ConcurrentHashMap<Integer, SecretKey> keyCache = new ConcurrentHashMap<>();

	public MasterKeyProvider(MasterKeyRepository masterKeyRepository) {
		this.masterKeyRepository = masterKeyRepository;
	}

	public SecretKey getCurrentKey() {
		return getKey(getCurrentVersion());
	}

	public int getCurrentVersion() {
		return masterKeyRepository.findByActiveTrue()
				.map(MasterKey::getVersion)
				.orElseThrow(() -> new IllegalStateException("No active master key configured."));
	}

	public SecretKey getKey(int version) {
		return keyCache.computeIfAbsent(version, this::loadKey);
	}

	public void evictAll() {
		keyCache.clear();
	}

	private SecretKey loadKey(int version) {
		MasterKey masterKey = masterKeyRepository.findByVersion(version)
				.orElseThrow(() -> new IllegalStateException(
						"Master key version " + version + " is not available."));
		byte[] keyMaterial = masterKey.getKeyMaterial();
		if (keyMaterial.length != MASTER_KEY_BYTES) {
			throw new IllegalStateException("Invalid master key material for version " + version + ".");
		}
		return new SecretKeySpec(keyMaterial.clone(), AES_ALGORITHM);
	}
}
