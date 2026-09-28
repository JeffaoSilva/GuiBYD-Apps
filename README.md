# Aplicativos Gui.BYD v3.0

Versão cliente para multimídias BYD desbloqueadas.

## Novidades da V3
- Ativação por licença numérica de 6 dígitos.
- Vínculo da licença à primeira instalação/multimídia.
- Identidade local com UUID aleatório + chave pública no Android Keystore.
- Validação da licença via Supabase.
- Tolerância offline de até 30 dias após uma validação válida.
- Métricas anônimas de uso dos botões.
- YouTube Morphe servido pelo Supabase Storage.
- Exibição da versão disponível do YouTube.
- Autoatualização do próprio Aplicativos Gui.BYD via release `guibyd`.
- Mantém package `com.guibyd.apps` e versionCode 3 / versionName 3.0.

## Interface
### Aplicativos do pendrive
- MicroG
- Electro
- Radarbot
- Spark
- Aurora Store

A pasta esperada continua sendo `USB/GuiBYD/`.

### Aplicativos do Aurora
- Chrome
- Disney Plus
- HBO Max
- Netflix
- Prime Video
- VLC
- Waze
- WhatsApp
- WhatsApp Business

### YouTube
O botão `Instalar / Atualizar o YouTube` consulta a release `youtube` no Supabase e baixa o APK do bucket privado `releases`.

## GitHub Secrets necessários
O workflow de release precisa destes 6 Secrets:
- `GUIBYD_KEYSTORE_B64`
- `GUIBYD_KEYSTORE_PASSWORD`
- `GUIBYD_KEY_ALIAS`
- `GUIBYD_KEY_PASSWORD`
- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`

Use os mesmos quatro Secrets de assinatura das versões anteriores para que a V3 atualize por cima da V2.

## APK gerado
`Aplicativos-GuiBYD-v3.0.apk`
