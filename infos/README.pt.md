<p align="center">
  <img src="./banners/banner_pt.png" alt="Banner do HandyReader" />
</p>

<p align="center">
  <em>Leia livremente, ouça sem limites.</em>
</p>

<p align="center">
  <a href="../README.md">English</a> | <a href="README.zh.md">中文</a> | <a href="README.fr.md">Français</a> | <a href="README.de.md">Deutsch</a> | <a href="README.es.md">Español</a> | <strong>Português</strong> | <a href="README.ru.md">Русский</a> | <a href="README.hi.md">हिन्दी</a> | <a href="README.ja.md">日本語</a> | <a href="README.ar.md">العربية</a>
</p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0.en.html"><img src="https://img.shields.io/github/license/EucWang/HandyReader" alt="Licença" /></a>
  <img src="https://img.shields.io/badge/minSdk-23-orange" alt="minSdk" />
  <img src="https://img.shields.io/badge/platform-Android-brightgreen" alt="Plataforma" />
  <a href="https://handyreader.top/download.html"><img src="https://img.shields.io/badge/Baixar-handyreader.top-blue" alt="Baixar" /></a>
</p>

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.wxn.reader"><strong>Disponível no Google Play</strong></a>
  &nbsp;·&nbsp;
  <a href="https://handyreader.top/download.html"><strong>Baixar APK</strong></a>
  &nbsp;·&nbsp;
  <a href="https://github.com/EucWang/HandyReader/releases"><strong>Releases no GitHub</strong></a>
</p>

---

O HandyReader é um leitor de livros eletrônicos e audiolivros gratuito e de código aberto para Android. Ele oferece suporte a uma ampla variedade de formatos, conta com um motor de texto para fala neural offline, traz uma personalização profunda com o Material You e mantém todos os seus dados no seu dispositivo.

## Capturas de tela

<table>
  <tr>
    <td width="25%" align="center"><img src="./screenshots/screenshot_bookshelf.webp" alt="Biblioteca" /></td>
    <td width="25%" align="center"><img src="./screenshots/screenshot_reading.webp" alt="Leitura" /></td>
    <td width="25%" align="center"><img src="./screenshots/screenshot_highlight.webp" alt="Destaques e notas" /></td>
    <td width="25%" align="center"><img src="./screenshots/screenshot_tts.webp" alt="Texto para fala" /></td>
  </tr>
  <tr>
    <td align="center"><sub>Biblioteca</sub></td>
    <td align="center"><sub>Leitura</sub></td>
    <td align="center"><sub>Destaques e notas</sub></td>
    <td align="center"><sub>Texto para fala</sub></td>
  </tr>
</table>

## Recursos

### 📚 Leitor multiformato
Leia livros eletrônicos e ouça audiolivros em um único aplicativo.
- **Livros eletrônicos**: EPUB, MOBI, AZW, AZW3, FB2, TXT, Markdown, HTML, PDF
- **Audiolivros**: MP3, M4A, AAC (com Media3/ExoPlayer)
- Motor de análise nativo em C++ (libmobi, libxml2, CSSParser) para velocidade e fidelidade

### 🎯 Texto para fala com IA offline
O recurso de destaque. Ouça qualquer livro com vozes neurais naturais — **totalmente offline, sem necessidade de internet**.
- Três motores: **IA neural offline** (sherpa-onnx), **Edge TTS** (online), **TTS do sistema** (alternativo)
- Reprodução em segundo plano com controles na notificação
- Velocidade e tom ajustáveis, temporizador de desligamento, avanço de capítulos
- Modelos de voz para baixar em vários idiomas

### 🎨 Design Material You e personalização profunda
- Cores dinâmicas no Android 12+ (tematização baseada no papel de parede com Material You)
- 12 esquemas de cores, 11 temas de leitura
- Fundos de leitura personalizados (cores sólidas ou imagens da galeria)
- Três origens de fontes: fontes do sistema, fontes do catálogo para baixar ou importe as suas

### 📝 Anotações e notas
- Destaques, sublinhados e notas com cores personalizadas
- Busca dentro do livro e filtragem de anotações
- Navegador de notas dedicado

### 📖 Catálogos OPDS online
- Navegue e baixe livros de catálogos OPDS 1.x online
- Suporte a autenticação (usuário/senha)
- Catálogos públicos integrados + adicione os seus

### 🃏 Cartões de citações
Gere belos cartões de citações compartilháveis a partir do texto selecionado.
- 5 estilos: branco minimalista, noite escura, pergaminho, pôster da capa, citação grande
- 4 proporções de aspecto (3:4, 1:1, 9:16, 4:5)
- Salve na galeria ou compartilhe diretamente

### 📊 Estatísticas de leitura
- Tempo de leitura diário, total e por livro
- Sequências de leitura (atual e mais longa)
- Mapa de calor dos hábitos de leitura
- Acompanhamento do progresso (não iniciado / em andamento / concluído)

### 🗂️ Biblioteca inteligente
- Prateleiras ilimitadas
- Layouts em grade e em lista
- Filtre por status, tipo de arquivo, última abertura, título ou progresso
- Ordene e organize grandes coleções

### 🔤 Dicionário e tradução
- Dicionário integrado (WordNet, ECDICT e mais)
- Tradução com IA online (modelo Meta m2/m100)
- Histórico de consultas
- Integração com aplicativos de dicionário externos

### 🔒 Privacidade em primeiro lugar
- Todos os dados armazenados localmente no seu dispositivo
- Sem análises ou rastreamento de terceiros
- Totalmente de código aberto sob a GPLv3

### 💾 Backup e restauração
- Exporte/importe seus dados de leitura (ZIP)
- Inclui progresso, anotações, notas, marcadores, prateleiras, estatísticas e preferências
- Mesclagem inteligente entre dispositivos baseada em hash de conteúdo

## Por que o HandyReader?

| | HandyReader | Leitores comuns |
|---|:---:|:---:|
| **TTS com IA offline** | ✅ Vozes neurais, sem internet | ❌ Apenas TTS do sistema |
| **Suporte a audiolivros** | ✅ MP3/M4A/AAC | ❌ Apenas livros eletrônicos |
| **Cobertura de formatos** | ✅ Mais de 12 formatos | ⚠️ Normalmente 3-5 |
| **Profundidade de personalização** | ✅ Temas, fontes, fundos, layouts | ⚠️ Limitada |
| **Código aberto** | ✅ GPLv3 | ⚠️ Muitas vezes fechado |
| **Cartões de citações** | ✅ Integrado | ❌ Raro |

## Primeiros passos

**Requisitos**: Android 6.0 (API 23) ou superior.

1. **Google Play** (recomendado para atualizações automáticas): [Instalar HandyReader](https://play.google.com/store/apps/details?id=com.wxn.reader)
2. **Download do APK** (dispositivos sem serviços Google são suportados): [Baixar em handyreader.top](https://handyreader.top/download.html)
3. **Releases no GitHub** (explore todas as versões): [Releases](https://github.com/EucWang/HandyReader/releases)

Abra um livro do seu dispositivo ou importe um — o HandyReader lidará com arquivos EPUB, MOBI, AZW3, FB2, TXT, MD, HTML, PDF e de audiolivros. Você também pode abrir arquivos enviados por outros aplicativos.

## Em breve

- 🔄 Sincronização do progresso de leitura via WebDAV
- 📡 Suporte a OPDS 2.0
- 📚 Suporte ao formato de quadrinhos CBR/CBZ
- 🎧 Formato de audiolivro M4B com suporte a capítulos
- 🎙️ Vozes online do Edge TTS
- 📄 Análise aprimorada de PDF / Markdown / HTML

> O projeto está em desenvolvimento ativo. Confira as [Releases](https://github.com/EucWang/HandyReader/releases) para ver as últimas mudanças.

## Stack tecnológica

| Categoria | Tecnologia |
|---|---|
| **Interface do usuário** | Jetpack Compose, Material Design 3, Navigation Compose |
| **Injeção de dependência** | Hilt (Dagger) |
| **Banco de dados** | Room |
| **Preferências** | DataStore |
| **Programação assíncrona** | Coroutines & Flow |
| **Carregamento de imagens** | Coil 3 |
| **Reprodução de mídia** | Media3 / ExoPlayer |
| **Texto para fala** | sherpa-onnx (neural offline), Edge TTS, Android TTS |
| **Análise** | libmobi (C++/JNI), jsoup, CSSParser |
| **Redes** | Ktor, OkHttp |

## Compilar a partir do código-fonte

Este projeto requer o **Android Studio Ladybug** (ou mais recente), o **JDK 17** e o **Android NDK**.

```bash
git clone https://github.com/EucWang/HandyReader.git
cd HandyReader
./gradlew :app:assembleDebug
```

> **Nota**: Os módulos nativos (`mobi`, `jp2forandroid`, `text2speech`) exigem o NDK para compilar. Compile no Windows, macOS ou Linux — consulte a documentação do projeto para detalhes.

<details>
<summary>Variantes de build</summary>

- `assembleDebug` — APK de depuração para desenvolvimento
- `assembleRelease` — APK de release (requer configuração de assinatura em `key.properties`)
- `bundleRelease` — AAB de release para o Google Play

</details>

## Licença

[![GNU GPLv3 License](./Licence.svg)](https://www.gnu.org/licenses/gpl-3.0.en.html)

Este projeto está licenciado sob a **GNU General Public License v3.0** — consulte o arquivo [LICENSE](../LICENSE) para detalhes.

## Agradecimentos

O HandyReader baseia-se no trabalho de muitos projetos de código aberto:

- [Skydoves](https://github.com/skydoves) — ColorPicker Compose
- [Shivamdhuria](https://github.com/Shivamdhuria) — Palette library
- [androidSpeech](https://github.com/gotev/android-speech) — Text-to-Speech
- [libmobi](https://github.com/bfabiszewski/libmobi) — MOBI/AZW library
- [tidy-html5](https://github.com/htacg/tidy-html5) — HTML tidy
- [utfcpp](https://github.com/nemtrif/utfcpp) — UTF-8 library
- [CSSParser](https://github.com/luojilab/CSSParser) — CSS parser
- [minizip](http://www.winimage.com/zLibDll/minizip.html) — ZIP library
- [jp2ForAndroid](https://github.com/EucWang/jp2ForAndroid) — JPEG2000 decoder
- [libxml2](https://gitlab.gnome.org/GNOME/libxml2) — XML parser
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) — Offline neural TTS engine
