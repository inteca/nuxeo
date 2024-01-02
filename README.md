# Inteca Nuxeo

Repozytorium Nuxeo sklonowane w celu naniesienia zmian pozwalających na integrację z Keycloakiem na środowiskach CABP.

Zmiany Inteca są naniesione na branchu `inteca/9.10`.

Poprawka ta naprawia komunikację z Nuxeo przez REST w momencie, kiedy autoryzujemy się przez adapter Keycloaka.
Po poprawce możliwe jest korzystanie z biblioteki https://github.com/nuxeo/nuxeo-java-client:
```java
client = new NuxeoClient.Builder()
            .url("http://192.168.11.8:8080/nuxeo")
            // Niestety działa tylko z tokenem
            // Autoryzacja Basic wywala się z błędem sugerującym, że w odpowiedzi z serwera
            // otrzymujemy blob binarny, a nie informację o zalogowanym użytkowniku.
            // Prawdopodobnie jest to spowodowane tym, że nasze Nuxeo integruje się z CALoginem,
            // który przy logowaniu po podaniu hasła wymaga jeszcze wybrania placówki pracownika.
            .authentication(new JWTAuthInterceptor("<token>"))
            .connect();
```

# Budowanie

Projekt należy budować z Maven niższym niż 3.8.1 (najnowszy do tej pory to chyba 3.6.3) - niektóre repozytoria, z których
korzysta Nuxeo dalej używają adresów HTTP...

Budować należy z katalogu głównego. Aby zbudować jakiś konkretny moduł, można spróbować użyć komendy
```
mvn clean install -DskipTests=true -pl nuxeo-services/login/nuxeo-platform-login-keycloak --also-make
```
Chociaż w praktyce i tak chyba budowany jest cały projekt.

Poza typowymi programami potrzebnymi do budowania projektów javowych (Java, Maven, Ant, etc.), do zbudowania tego projektu potrzebne są następujące programy, dostępne z poziomu konsoli:
- npm (https://www.npmjs.com/)
- bower (można zainstalować za pomocą npm-a: `npm install -g bower`)
- grunt (można zainstalować za pomocą npm-a: `npm install -g grunt-cli`)

W głównym katalogu projektu (zawierającym ten plik README) uruchomić `mvn clean install -DskipTests=true`.  
Na stosunkowo porządnej maszynie (Ryzen 5500U + 16GB RAM) build trwa nieco ponad 15 minut.  
Niektóre testy się wywalają - nie wnikałem dlaczego.

Zbudowane pliki, które potrzebujemy skopiować:
- `./nuxeo-services/login/nuxeo-platform-login-keycloak/target/nuxeo-platform-login-keycloak-*.jar`

# Nanoszenie zmian na Nuxeo

Jak się połączyć do naszych serwerów Nuxeo po SSH - opisane na wiki.

`${nuxeo_home}` - zwykle `/opt/nuxeo` albo `/opt/nuxeo/server`

- Do `${nuxeo_home}/templates` skopiować katalog `inteca/keycloak` z tego repozytorium, zawierający pliki jar, konfigurację oraz deskryptory dla modułu keycloak
    - Zmiany w jarach w tym katalogu opisane w sekcji: https://github.com/inteca/nuxeo-keycloak-cmis-poc#steps-to-configure-nuxeo
    - `scp -r ./inteca/keycloak <serwer>:${nuxeo_home}/templates/keycloak`
    - `docker cp ./inteca/keycloak <pod>:${nuxeo_home}/templates/keycloak`
- Skopiować plik `${nuxeo_home}/templates/keycloak/nxserver/config/keycloak.json` do `${nuxeo_home}/nxserver/config/keycloak.json`
- Skopiować plik `/etc/nuxeo/nuxeo.conf` (chyba jest generowany przy uruchomieniu serwera nuxeo, więc może go początkowo nie być) do katalogu `{nuxeo_home}/bin`
    - Zmienić w tym pliku property `nuxeo.templates`, dodając po przecinku `keycloak`

# Logi do debugowania
- `/opt/nuxeo/server/log`
- `/var/log/nuxeo` - najbardziej przydatne
