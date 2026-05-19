import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class CryptoService {

  private readonly algorithm: RsaHashedKeyGenParams = {
    name: 'RSA-OAEP',
    modulusLength: 2048,
    publicExponent: new Uint8Array([1,0,1]),
    hash: 'SHA-256'
  };

  private readonly importAlgo: RsaHashedImportParams = {
    name: 'RSA-OAEP',
    hash: 'SHA-256'
  };

  async generateKeyPair(): Promise<CryptoKeyPair> {
    return crypto.subtle.generateKey(this.algorithm, true, ['encrypt', 'decrypt']);
  }

  async exportPublicKeyPem(pub: CryptoKey): Promise<string> {
    const spki = await crypto.subtle.exportKey('spki', pub);
    return wrapPem(bytesToBase64(new Uint8Array(spki)), 'PUBLIC KEY');
  }

  async exportPrivateKeyPem(priv: CryptoKey): Promise<string> {
    const pkcs8 = await crypto.subtle.exportKey('pkcs8', priv);
    return wrapPem(bytesToBase64(new Uint8Array(pkcs8)), 'PRIVATE KEY');
  }

  async importPrivateKeyPem(pem: string): Promise<CryptoKey> {
    const b64 = stripPem(pem);
    if (!b64) {
      throw new Error('No key material found.');
    }
    const der = base64ToBytes(b64);
    return crypto.subtle.importKey('pkcs8', der as BufferSource, this.importAlgo, false, ['decrypt']);
  }

  async decryptChallenge(priv: CryptoKey, ciphertextBase64: string): Promise<string> {
    const ct = base64ToBytes(ciphertextBase64);
    const plain = await crypto.subtle.decrypt({ name: 'RSA-OAEP' }, priv, ct as BufferSource);
    return bytesToBase64(new Uint8Array(plain));
  }
}

function bytesToBase64(bytes: Uint8Array): string {
  let s = '';
  for (let i = 0; i < bytes.length; i++) {
    s += String.fromCharCode(bytes[i]);
  }
  return btoa(s);
}

function base64ToBytes(b64: string): Uint8Array {
  const bin = atob(b64.replace(/\s+/g, ''));
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) {
    bytes[i] = bin.charCodeAt(i);
  }
  return bytes;
}

function wrapPem(base64Body: string, label: string): string {
  const lines: string[] = [];
  for (let i = 0; i < base64Body.length; i += 64) {
    lines.push(base64Body.slice(i, i + 64));
  }
  return `-----BEGIN ${label}-----\n${lines.join('\n')}\n-----END ${label}-----\n`;
}

function stripPem(pem: string): string {
  return pem
    .replace(/-----BEGIN [^-]+-----/g, '')
    .replace(/-----END [^-]+-----/g, '')
    .replace(/\s+/g, '');
}