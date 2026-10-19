# AutoDodge — calibração e aprendizado local (v0.3 experimental)

**O que é:** um projeto Android Studio de código-fonte, NÃO compilado neste ambiente, NÃO testado no Xiaomi 14C. Detectar projéteis com precisão em Brawl Stars ainda requer testes e calibração. A detecção não é um modelo de IA treinado com imagens reais do jogo.

## Novidades da v0.3

- Identificação da versão no aplicativo e incremento de versão Android para facilitar instalações/atualizações.
- Instruções de calibração e limitações reunidas para o primeiro teste no Xiaomi 14C.
- Mantém feedback manual “É tiro / Não é tiro” e aprendizado local da v0.2; não é uma IA treinada com vídeos reais.
- Esta versão continua sendo código-fonte experimental; o APK precisa ser compilado e testado no aparelho.

## Fluxo implementado

1. Inicie o aplicativo e permita aparecer sobre outros apps e a acessibilidade (gestos).
2. Ajuste a posição de seu personagem e do analógico na **tela paisagem**. O centro do personagem pode variar por cena, mapa e situação.
3. Toque em **Iniciar captura + abrir Brawl Stars** e permita compartilhar **a tela inteira** na janela do Android.
4. Quando o jogo abrir, haverá uma checkbox **AutoDodge**; ao ativá-la o app examina as imagens na memória (até ~14 fps (dependendo do aparelho)).
5. O detector agrupa pixels móveis, segue objetos entre quadros e estima quais estão se aproximando do personagem. Se a trajetória calculada ameaça cruzar o personagem, faz um gesto breve na região do analógico esquerdo.
6. O classificador visual aprende padrões dos objetos em movimento com atualização *online*, começando por **exemplos fracos** provenientes de trajetórias consistentes. Para melhorar os rótulos, você pode tocar **Ensinar ▾**, e logo após a detecção de um candidato tocar **✓ É tiro** ou **✕ Não é**. Essas correções recebem peso maior no treino.
7. Os pesos do pequeno classificador (números) são salvos localmente por `SharedPreferences`, persistindo entre sessões. Não são salvas imagens ou vídeos.

**Limitação importante de aprendizado:** isto é visão computacional heurística + classificador online, não visão mágica. Exemplos criados automaticamente PODEM ser falsos e piorar o aprendizado; corrigir exemplos ajuda, mas não garante reconhecimento real. Tiros que mudam drasticamente de aparência, projéteis rápidos, efeitos de mapa ou movimentação da câmera podem ser ignorados/confundidos. O detector não lê dados internos do jogo. Não há qualquer modelo pré-treinado nem acesso à câmera: a fonte é **captura autorizada da tela**.

## Compilar o APK

### Com Android Studio (computador)

- Abra esta pasta como projeto no Android Studio, instale o SDK Android 35 e use JDK 17.
- Sincronize com AGP 8.6.1 e Gradle 8.7. O wrapper é especificado em `gradle/wrapper/gradle-wrapper.properties`, mas o JAR do wrapper não está incluso: o Android Studio pode exigir configurar Gradle local (ou gerar o wrapper com `gradle wrapper --gradle-version 8.7`).
- No menu, selecione **Build > Build Bundle(s) / APK(s) > Build APK(s)** e instale o `app/build/outputs/apk/debug/app-debug.apk`.

### Sem PC: GitHub Actions

- Crie um repositório **privado** e adicione os arquivos extraídos da pasta raiz do ZIP, mantendo `.github/workflows/build-apk.yml`.
- Em **Actions**, execute **Compilar APK experimental**.
- Após compilação, baixe o artefato `autododge-v0.3-experimental-debug` e instale no Android. Não foi compilado aqui, logo erros de build são possíveis.

## Android / Xiaomi / Shizuku

- **MediaProjection** oferece captura de tela mediante autorização explícita. No Android 14+ o usuário deve autorizar **cada nova sessão**; o app inicia um foreground service dedicado.
- **AccessibilityService** é usado para `dispatchGesture` no analógico. O Android documenta que um gesto enviado pode **cancelar os toques existentes**, interferindo no controle manual e nos ataques, portanto os gestos foram limitados por intervalo. Funciona apenas se o serviço for habilitado.
- **Shizuku não é necessário** para o código atual. O HyperOS pode exigir permissões adicionais para sobreposição e gestão de bateria.
- Se ativar o jogo e nada acontecer, verifique as permissões, recapture a tela completa e ajuste os 4 sliders. Desative durante partidas se causar movimentação indesejada.
- Interagir automaticamente com a jogabilidade pode contrariar as regras da Supercell e gerar punição de conta.

## Privacidade

O aplicativo não pede INTERNET; os quadros de tela são processados em memória e não são salvos. Somente as preferências, a posição dos controles e **pesos numéricos do classificador** são persistidos. Ele não acessa a memória interna nem modifica os arquivos do Brawl Stars.

## Teste sem Android

O detector em `ProjectileDetector.java` é Java puro. Execute:

```sh
javac -d /tmp/autododge_classes app/src/main/java/br/com/experimento/autododge/ProjectileDetector.java tests/DetectorTest.java
java -cp /tmp/autododge_classes DetectorTest
```

O teste passa para restauração de parâmetros e tela sem movimento; **não valida tiros reais, nem o aparelho**.


## Sobre root

O root do Xiaomi 14C não é usado diretamente nesta versão: a captura usa MediaProjection e o gesto usa Acessibilidade. Isso evita depender de comandos específicos do sistema. Se o controle por acessibilidade falhar no HyperOS, a próxima etapa é testar compatibilidade e permissões no aparelho antes de acrescentar integração root.
