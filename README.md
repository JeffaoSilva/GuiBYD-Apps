# Aplicativos Gui.BYD

Launcher simples em paisagem para multimídias BYD desbloqueadas.

## Fluxo
1. Abrir Aplicativos Gui.BYD.
2. Tocar em um app.
3. O Android envia o link da ficha da Google Play diretamente ao pacote `com.aurora.store`.
4. O Aurora deve abrir a ficha correspondente.
5. Instalar no Aurora e voltar ao Gui.BYD.
6. Repetir para o próximo app.

## Sem fallback
Se o Aurora não existir, o app mostra:
> Aurora Store necessário

Ele NÃO abre Play Store e NÃO abre navegador.

## Apps
- Chrome
- Disney Plus
- HBO Max
- Netflix
- Prime Video
- VLC
- Waze
- WhatsApp
- WhatsApp Business

## Teste principal
Validar no Aurora Store 4.8.4:
- se o primeiro clique abre diretamente a ficha correta;
- se, com o Aurora já aberto, um segundo clique troca corretamente para a nova ficha.

## Gerar APK com GitHub Actions
Suba o conteúdo desta pasta para um repositório GitHub.
A ação `.github/workflows/build-apk.yml` gera automaticamente:
`app-debug.apk`

O APK fica disponível em:
Actions > execução Build APK > Artifacts > GuiBYD-Apps-debug
