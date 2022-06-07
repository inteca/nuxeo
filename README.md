# Inteca Nuxeo

Repozytorium Nuxeo sklonowane w celu naniesienia zmian pozwalających na integrację z Keycloakiem na środowiskach CABP.

Zmiany Inteca są naniesione na branchu `inteca/9.10`.

# Budowanie

Poza typowymi programami potrzebnymi do budowania projektów javowych (Java, Maven, Ant, etc.), do zbudowania tego projektu potrzebne są następujące programy, dostępne z poziomu konsoli:
- npm (https://www.npmjs.com/)
- bower (można zainstalować za pomocą npm-a: `npm install -g bower`)
- grunt (można zainstalować za pomocą npm-a: `npm install -g grunt-cli`)

W głównym katalogu projektu (zawierającym ten plik README) uruchomić `mvn clean install -DskipTests=true`.  
Na stosunkowo porządnej maszynie (Ryzen 5500U + 16GB RAM) build trwa nieco ponad 15 minut.  
Niektóre testy się wywalają - nie wnikałem, dlaczego.
