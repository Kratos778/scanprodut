# Stock Scan – Kotlin nativo (Angola)

App Android 100% offline em **Kotlin** para controlo de stock com scanner de código de barras.

## Funcionalidades

- **Entrada**: scaneia → produto novo pede nome/categoria/preço → +quantidade
- **Venda**: scaneia para carrinho → Confirmar (subtrai tudo de uma vez, bloqueia se faltar)
- **Stock**: lista, editar, +/−, apagar, pesquisa, alerta stock baixo
- **Histórico** + desfazer última venda
- Quantidade ×1 / ×2 / ×6 / ×12
- Preço em Kz + total da venda
- Lanterna
- Room (base de dados local)

## Stack

- Kotlin + Jetpack Compose
- CameraX + ML Kit Barcode Scanning (offline)
- Room
- Min SDK 26

## Como gerar o APK

1. Sobe **todos** estes ficheiros para o repositório
2. Vai a **Actions → Build APK → Run workflow**
3. Descarrega o artefacto `stock-scan-apk`

Feito para Elizier Dias – Angola.
