

## **Korisničke uloge**

* **Administrator (Admin aplikacije)**

* **CA korisnik (korisnik organizacije koja poseduje CA sertifikat)**

* **Običan korisnik**

---

## **Prava i funkcionalnosti po ulogama**

### **Administrator**

* Može da dodaje nove CA korisnike.  
* Može da dodaje CA sertifikate za ove korisnike (za sisteme, servere itd).  
* Može da izdaje sve tipove sertifikata:  
  * Root (samopotpisani)  
  * Intermediate  
  * End-Entity (EE)  
* Ima uvid u sve sertifikate u sistemu.  
* Može da koristi bilo koji sertifikat iz bilo kog lanca za dalje izdavanje.  
* Može da vidi, preuzme ili povuče sve sertifikate.

### **CA korisnik**

* Može da izdaje intermediate i end-entity sertifikate **samo za svoju organizaciju**, koristeći svoj CA sertifikat ili bilo koji sertifikat iz prethodno kreiranog lanca.  
* Može da vidi i preuzme samo sertifikate iz svoje organizacije.

### **Običan korisnik**

* Može da:  
  * uploaduje CSR radi izdavanja sertifikata,  
  * ili popuni formu za generisanje ključeva i sertifikata,  
  * bira CA sertifikat koji će se koristiti za izdavanje njegovog sertifikata  
  * preuzme dobijeni sertifikat i privatni ključ,  
  * pregleda svoje sertifikate,  
  * povuče sertifikat uz navođenje razloga (po X.509 standardu).

---

## **Funkcionalnosti sistema**

1. ### **Registracija** 

Samo obični korisnici mogu da se registruju, na početnoj stranici putem forme za registraciju. Običan korisnik od podataka mora da unese *email* adresu, lozinku, ime, prezime i organizaciju. Lozinka mora da se unese dva puta, i mora da ispoštuje [minimalne zahteve](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html). Korisnik mora da potvrdi svoj identitet nakon registracije, klikom na aktivacioni link iz mejla. Link je vremenski ograničen i može da se iskoristi samo jednom. Da bi se korisnik uspešno registrovao, pored osnovnih podataka on mora i da unese svoj javni ključ. Ovaj javni ključ se potom čuva na serveru za tog korisnika, i služi za proveru identiteta u slučaju zaboravljene lozinke. Potrebno je validirati javni ključ tako što će prilikom registracije server enkriptovati neki random string tim javnim ključem. Prilikom aktivacije naloga, korisnik mora da unese odgovarajući javni ključ kako bi potvrdio svoj identitet. Korisnik dekriptuje dobijeni string svojim privatnim ključem. Server potom proverava poklapanje originalnog i dekriptovanog stringa, i kompletira registraciju ako se ove vrednosti poklapaju. 

Privatni ključ se nikada ne šalje i ne čuva na serveru. Za dekripciju poruke, potrebno je implementirati WebCryptoAPI i izvršti dekripciju lokalno, u *browseru*.

Dodatno, korisniku treba omogućiti mehanizam oporavka naloga (Zaboravljena lozinka). U ovom slučaju, korisnik takođe dobija string enkriptovan svojim javnim ključem. Ukoliko uspešno dekriptuje zadati string, biće mu omogućen ulaz u sistem i promena lozinke.

2. ### **Registracija CA korisnika** 

Administrator sistema može da dodaje nove organizacije i CA korisnike u tim organizacijama. Nakon završene registracije, CA korisnik dobija mejl sa aktivacionim linkom koji ga vodi stranicu za promenu lozinke. 

3. ### **Prijava na sistem** 

Svi korisnici sistema imaju mogućnost prijave pomoću email adrese i lozinke. Ukoliko je korisnik uspešno prijavljen, potrebno je izgenerisati *access* token i *refresh* token koji će se poslati na klijentski deo aplikacije. Access token treba da ima kratak životni vek, dok refresh token ima značajno duži. 

Dobijene tokene treba skladištiti na klijentskom delu; access token treba slati kroz zaglavlje prilikom narednih zahteva tog ulogovanog klijenta, dok ne istekne. Po isteku, kreirati novi access token koristeći refresh token. 

Pročitajte više o bezbednoj implementaciji i bezbednom korišćenju: [https://auth0.com/blog/refresh-tokens-what-are-they-and-when-to-use-them/](https://auth0.com/blog/refresh-tokens-what-are-they-and-when-to-use-them/) 

4. ### **Dvofaktorska autentikacija** 

Običan korisnik može u okviru svog profila da odabere obaveznu dvofaktorsku autentikaciju. Dvofaktorska autentikacija podrazumeva da korisnik pored lozinke unese i sigurnosni kod generisan pomoću Authenticator aplikacije. Za potrebe povezivanja, možete koristiti [Google](https://play.google.com/store/apps/details?id=com.google.android.apps.authenticator2&hl=en) ili [Microsoft](https://support.microsoft.com/sr-Latn-RS/authenticator/download-microsoft-authenticator) authenticator aplikacije.

5. ### **Izdavanje sertifikata** 

Sistem treba da podrži centralizovano izdavanje sertifikata za digitalne entitete svih nivoa, uključujući proizvoljan broj **intermediate nivoa**. Za generisanje sertifikata korisnik unosi podatke o vlasniku (pogledati [X500Name](https://docs.oracle.com/middleware/12211/infrasecurity/SDTPJ/oracle/security/crypto/cert/X500Name.html)), definiše trajanje sertifikata i ekstenzije (npr. keyCertSign, BasicConstraints, …) i bira CA sertifikat koji će biti izdavalac. Prilikom generisanja sertifikata, potrebno je voditi računa o validnosti sertifikata izdavaoca. Validnost treba posmatrati u kontekstu perioda važenja, ispravnosti digitalnog potpisa i statusa povučenosti.

* Izdavanje:  
  * Samopotpisanih (Root CA)  
  * Intermediate (CA)  
  * End-Entity (korisnici, uređaji)

6. ### **Čuvanje sertifikata** 

Poverljivost sertifikata mora biti očuvana kroz sistem. Preporučujemo da proučite kako bi takvo nešto u praksi moglo da izgleda: [https://infisical.com/docs/internals/security\#cryptography](https://infisical.com/docs/internals/security#cryptography)

* Minimalno je potrebno obezbediti sledeće:  
  * Da se privatni ključevi izdatih sertifikata u bazi čuvaju enkriptovani  
  * Da se oni enkriptuju pomoću ključa izdatog za pojedinačnu organizaciju  
  * Da je konfiguracija kriptografskih algoritama dobro proučena, da su dobre prakse istražene i primenjene  
  * Da je obezbeđena poverljivost i samih ključeva na nivou organizacija (što sugeriše postojanje glavnog (master) ključa, ali detalje ostavljamo na izbor timu)

7. ### **Preuzimanje sertifikata** 

Prilikom preuzimanja sertifikata i povezanog privatnog ključa, obezbediti da se oni na serverskoj strani zapakuju u keystore (pkcs12 ili jks). S obzirom da keystore format nije čitljiv, potrebno je pripremiti alat koji će moći da pročita sadržaj ovoj fajla (poput openssla, portekle i sl). Ukoliko se sertifikat preuzima bez privatnog ključa, treba omogućiti preuzimanje u .cer ili .pem formatu.

8. ### **CSR (Certificate Signing Request)** 

CSR je zahtev koji pravi entitet kako bi zatražio sertifikat of sertifikacionog tela (CA). CSR sadrži sve podatke o vlasniku sertifikata (pogledati [X500Name](https://docs.oracle.com/middleware/12211/infrasecurity/SDTPJ/oracle/security/crypto/cert/X500Name.html)), javni ključ i ekstenzije. End-entity korisnici prave CSR putem eksterne generacije \- korisnik sam generiše ključeve i koristi alat za generisanje csr-a, poput openssl-a. Tako generisani csr se potom čuva u enkodovanom .pem formatu i upload-uje kroz formu na PKI sistemu. Pored upload-a je potrebno omogućiti korisniku da odabere CA za digitalni potpis. Na osnovu odabranog CA, korisnik može da unese trajanje sertifikata pri čemu se mora poštovati trajanje sertifikata odabranog CA. Druga opcija za generisanje EE sertifikata je *autogenerate* pri čemu PKI sistem generiše par ključeva, ali ne čuva privatni ključ. Privatni ključ može da se preuzme uz sertifikat u pkcs12 ili jks formatu (pogledati funkcionalnost preuzimanje sertifikata), odmah nakon generisanja sertifikata. Korisnik ne može naknadno da preuzme privatni ključ, može samo da preuzme sertifikat u .cer ili .pem formatu. U slučaju autogenerate opcije, korisnik popunjava formu za kreiranje sertifikata uključujući datume, odabir CA i lične podatke *(CN, O, OU, C, E).*

9. ### **Pregled i pristup sertifikatima** 

* Administrator vidi sve sertifikate.  
* CA korisnik vidi samo sertifikate iz svog lanca.  
* Korisnik vidi samo svoje EE sertifikate (može da ih ima proizvoljno mnogo).

10. ### **Povlačenje sertifikata (Revocation)** 

Svaki korisnik ima mogućnost povlačenja sertifikata uz obavezno navođenje razloga iz X.509 standarda. Povučeni sertifikati ne bi smeli da se ponude dalje za izdavanje novih sertifikata (isto važi i za privatni ključ vezan za povučeni sertifikat). Za proveru povučenosti, potrebno je obezbediti Revocation servis (CRL proveru) pomoću CRL Distribution Point ekstenzije u sertifikatu ili podršku za OCSP.

11. ### **Rotacija master ključa 12**

Potrebno je omogućiti periodičnu rotaciju master ključa. Nakon određenog vremena (npr. na svkih 6 meseci ili godinu dana) potrebno je izgenerisati novi master ključ i zameniti postojeći. Voditi računa da ova rotacija utiče i na sve podatke enkriptovane master ključem, pa je potrebno ovežiti enkripciju tih podataka. Ova funkcionalnost ne sme da naruši performanse sistema tokom redovne upotrebe.  
---

## **Opšti zahtevi**

Potrebno je obezbediti komunikaciju klijentskog i serverskog dela aplikacije putem HTTPS protokola. To podrazumeva da izgenerišete nov sertifikat, i iskonfigurišete aplikaciju tako da iskoristi taj sertifikat za HTTPS komunikaciju između svih servisa aplikacije. Ukoliko tim izgeneriše novi sertifikat pomoću svog PKI sistema, dobiće dodatne bodove za ovu funkcionalnost.

Neophodno je implementirati autentikaciju i kontrolu pristupa svih korisnika. Korisnici koji nisu autentifikovani nemaju prava pristupa ni jednoj stranici, osim stranici za registraciju i prijavu na sistem. Takođe nemaju prava pristupa nikakvim podacima sistema. Potrebno je obezbediti zaštitu pristupa za svaki ulaz u sistem (engl. endpoint) i na klijentskoj i na serverskoj strani aplikacije.

## **Podela funkcionalnosti po članovima tima**

Student 1:

- 1  
- 2  
- 5 (izdavanje sertifikata kao administrator, provera validnosti)  
- 9 (prikaz za administratora)  
- 10  
- HTTPS

Student 2:

- 3  
- 5 (izdavanje sertifikata kao CA korisnik, ekstenzije)  
- 7 (preuzimanje sa privatnim ključem)  
- 9 (prikaz za CA korisnika)  
- 11

Student 3:

- 4  
- 6  
- 7 (preuzimanje bez privatnog ključa)  
- 8  
- 9 (prikaz za običnog korisnika)

### **Kontrolne tačke**

1. kontrolna tačka

Kontrolna tačka 1 će se održati krajem aprila i nosiće 15 bodova. Za potrebe ove kontrolne tačke možete direktno ubaciti u bazu različite korisnike. Potrebno je uraditi sledeće funkcionalnosti:

Student 1: 

* 9   
* 5   
* 2 

Student 2:

* 3  \- bez generisanja i upotrebe refresh tokena  
* 5 

Student 3:

* 6   
* 9 

2. kontrolna tačka

Kontrolna tačka 2 će se održati krajem maja i nosiće 10 bodova. Potrebno je uraditi sledeće funkcionalnosti:

Student 1: 

* 1 

Student 2:

* 3 \- u potpunosti sa *refresh* tokenima  
* 7 

Student 3:

* 7   
* 4 

