import { Injectable } from '@angular/core';

export type StoredTokens = {
  accessToken: string;
  accessExpiresInSeconds: number;
  storedAtMs: number;
};

const KEY = 'ib.auth.tokens.v1';

@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  private storage: Storage = sessionStorage;

  setSessionStorage() {
    this.storage = sessionStorage;
  }

  setLocalStorage() {
    this.storage = localStorage;
  }

  read(): StoredTokens | null {
    const raw = this.storage.getItem(KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as StoredTokens;
    } catch {
      this.storage.removeItem(KEY);
      return null;
    }
  }

  write(tokens: Omit<StoredTokens, 'storedAtMs'>) {
    const payload: StoredTokens = { ...tokens, storedAtMs: Date.now() };
    this.storage.setItem(KEY, JSON.stringify(payload));
  }

  clear() {
    this.storage.removeItem(KEY);
  }
}

