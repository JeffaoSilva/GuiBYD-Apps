# Aplicativos Gui.BYD V2

## Interface
Tudo em uma única tela, em 3 colunas:

1. Aplicativos do pendrive
2. Aplicativos do Aurora
3. Atualização do YouTube

A orientação não é travada. O Android pode alternar entre retrato e paisagem.

## Aplicativos do pendrive

O aplicativo procura a pasta `GuiBYD` na raiz da mídia removível.

Busca por prefixo, sem exigir `_` após o nome:

- YouTube -> `Youtube_Morphe*.apk`
- MicroG -> `MicroG_RE*.apk`
- Electro -> `Electro*.apk`
- Radarbot -> `Radarbot*.apk`
- Spark -> `Spark*.apk`
- Aurora Store -> `Aurora_Store*.apk`

Se houver mais de um arquivo compatível, o app compara os números presentes no nome
e tenta usar a versão mais alta.

## Aplicativos do Aurora

- Chrome
- Disney Plus
- HBO Max
- Netflix
- Prime Video
- VLC
- Waze
- WhatsApp
- WhatsApp Business

Cada botão abre a ficha correspondente no Aurora Store.

## Atualização online do YouTube

O botão `Atualizar YouTube` baixa:

https://github.com/JeffaoSilva/GuiBYD-Apps/releases/latest/download/Youtube_Morphe.apk

Na release mais recente do GitHub, o asset deve se chamar exatamente:

`Youtube_Morphe.apk`

Após o download, o app abre o instalador do Android.

## Assinatura de produção

O workflow de release espera estes GitHub Secrets:

- `GUIBYD_KEYSTORE_B64`
- `GUIBYD_KEYSTORE_PASSWORD`
- `GUIBYD_KEY_ALIAS`
- `GUIBYD_KEY_PASSWORD`

A chave privada NÃO deve ser enviada para o repositório público.

## Versão
- package: `com.guibyd.apps`
- versionCode: 2
- versionName: 2.0
