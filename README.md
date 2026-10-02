# Stock Scan – Controlo de stock offline (Angola)

App Android 100% offline para controlo de stock com scanner de código de barras / QR.

Funciona como as caixas de supermercado: cada **tipo de produto** tem o seu código de barras fixo. Scaneias → +1 ou −1 na quantidade desse produto.

## O que a app faz

- **Entrada**: escolhe categoria (opcional) → scaneia → produto novo pede nome/categoria/preço → +quantidade
- **Venda**: scaneia para o carrinho → Confirmar venda (subtrai tudo de uma vez, bloqueia se faltar stock)
- **Stock**: lista por categoria, editar nome/categoria/preço/mínimo, +/− manual, apagar, pesquisa e filtro
- **Histórico**: entradas e vendas com data/hora + desfazer última venda
- Quantidade manual ao scanear (×1, ×2, ×6, ×12 ou número livre)
- Preço em Kz + total da venda + total do dia
- Alerta de stock baixo
- Backup JSON / CSV + importar
- Backup automático interno (Filesystem)
- Som + vibração (desligável) + lanterna
- Tema claro/escuro

## Ficheiros do projecto

```
www/index.html          ← toda a app (HTML+CSS+JS)
package.json
capacitor.config.json
.github/workflows/build-apk.yml
README.md
```

## Como gerar o APK pelo telemóvel (GitHub Actions)

### 1. Estrutura correcta
Só estes ficheiros. Nada na raiz excepto package.json, capacitor.config.json e README.md.

### 2. Keystore fixa (obrigatório para actualizações sem perder dados)

No telemóvel (Termux ou PC uma vez só):

```bash
keytool -genkey -v -keystore release.keystore -alias stockscan -keyalg RSA -keysize 2048 -validity 10000
```

Responde às perguntas (nome, password, etc.). Guarda bem a password e o alias.

Converte para base64:

```bash
base64 -w 0 release.keystore > keystore.txt
```

No GitHub → o teu repositório → **Settings → Secrets and variables → Actions → New repository secret**:

| Nome do Secret       | Valor                          |
|----------------------|--------------------------------|
| `KEYSTORE_BASE64`    | conteúdo completo do keystore.txt |
| `KEYSTORE_PASSWORD`  | a password que puseste         |
| `KEY_PASSWORD`       | normalmente a mesma password   |
| `KEY_ALIAS`          | `stockscan` (ou o alias que escolheste) |

### 3. Dispara o build
- Faz push para `main` ou vai a **Actions → Build APK → Run workflow**
- Quando terminar, descarrega o artefacto `stock-scan-apk` → ficheiro `.apk`
- Instala no telemóvel (permite fontes desconhecidas)

**Importante**: com a keystore nos Secrets, todos os builds futuros são assinados com a mesma chave → podes actualizar a app por cima sem desinstalar e **sem perder os dados**.

### 4. Primeira instalação
1. Instala o APK
2. Abre → permite a câmara quando pedir
3. Vai a **Entrada** → Ligar câmara → scaneia o primeiro produto
4. Preenche nome, categoria e (opcional) preço

## Notas técnicas

- Dados em `localStorage` (pasta privada da app) + backup automático em `Directory.Data`
- Se desinstalares a app, perdes os dados locais → exporta backup de vez em quando
- Leitor: `html5-qrcode` 2.3.8 embutido (offline, sem CDN)
- Capacitor 6 + Java 17 + Node 20
- Permissão CAMERA injectada no workflow

## Actualizar a app mais tarde

1. Altera o `www/index.html` (ou outros ficheiros)
2. Push para o GitHub
3. Espera o Actions gerar o novo APK
4. Instala por cima da versão antiga → dados mantêm-se

---
Feito para o Elizier Dias – Angola.
