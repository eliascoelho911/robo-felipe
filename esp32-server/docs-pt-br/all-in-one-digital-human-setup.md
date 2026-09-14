# Guia de configuração do digital human all-in-one (一体机)

Este projeto serve para implantar um sistema completo de exibição de digital human em dispositivos de arquitetura x86 (como mini PCs, computadores industriais, computadores comuns etc.), implementando as seguintes funcionalidades:
- Ao ligar, entra automaticamente no navegador em modo Kiosk em tela cheia, exibindo a página definida
- Executa em segundo plano o serviço de detecção de wake word, com suporte a interação por voz

> **Nota**: este documento demonstra a implantação usando como exemplo o **mini PC Intel N100 (天虹 QN10-100B4)**; outros dispositivos x86 podem se basear nele com os devidos ajustes (atenção às diferenças de configuração de rede e de dispositivo de placa de som).

## Ambiente aplicável

| Item | Descrição |
|------|------|
| Hardware de exemplo | 天虹 QN10-100B4 (Intel N100) |
| Sistema operacional | Ubuntu 24.04 LTS (Noble Numbat) |
| Usuário de exemplo | xz (substitua conforme a sua realidade) |
| Rede | Conexão Wi-Fi, IP fixo (pode mudar para cabo se necessário) |

## Fluxo de implantação

1. Inicialização do sistema (troca de mirrors, conexão de rede)
2. Instalação dos componentes gráficos e do navegador Kiosk
3. Configuração do login automático e da interface gráfica
4. Implantação do serviço de wake word (ambiente Python + microfone)
5. Otimização da velocidade de boot e ocultação das mensagens de inicialização

---


### Inicialização do sistema (troca de mirrors, conexão de rede)

```
sudo cp /etc/apt/sources.list /etc/apt/sources.list.bak

sudo tee /etc/apt/sources.list > /dev/null <<EOF
deb http://mirrors.aliyun.com/ubuntu/ noble main restricted universe multiverse
# deb-src http://mirrors.aliyun.com/ubuntu/ noble main restricted universe multiverse

deb http://mirrors.aliyun.com/ubuntu/ noble-security main restricted universe multiverse
# deb-src http://mirrors.aliyun.com/ubuntu/ noble-security main restricted universe multiverse

deb http://mirrors.aliyun.com/ubuntu/ noble-updates main restricted universe multiverse
# deb-src http://mirrors.aliyun.com/ubuntu/ noble-updates main restricted universe multiverse

deb http://mirrors.aliyun.com/ubuntu/ noble-proposed main restricted universe multiverse
# deb-src http://mirrors.aliyun.com/ubuntu/ noble-proposed main restricted universe multiverse

deb http://mirrors.aliyun.com/ubuntu/ noble-backports main restricted universe multiverse
# deb-src http://mirrors.aliyun.com/ubuntu/ noble-backports main restricted universe multiverse
EOF

echo 'Acquire::ForceIPv4 "true";' | sudo tee /etc/apt/apt.conf.d/99force-ipv4
```


Instale as ferramentas de gerenciamento de rede (ignore se já existirem)

Bash

```
sudo apt update
sudo apt install network-manager -y
sudo systemctl start NetworkManager
sudo systemctl enable NetworkManager
```


Configure a senha do Wi-Fi e o IP fixo

> **Atenção**: o nome do Wi-Fi, a senha e o endereço IP nos comandos abaixo são exemplos; substitua obrigatoriamente pelas suas informações reais.

Bash

```
sudo nmcli device wifi connect "MERCURY_1812" password "12345678"

sudo nmcli connection modify "MERCURY_1812" ipv4.addresses "192.168.0.86/24" ipv4.gateway "192.168.0.1" ipv4.dns "8.8.8.8,114.114.114.114" ipv4.method "manual"

sudo nmcli connection up "MERCURY_1812"
```


### Passo 1: instalar os componentes gráficos essenciais e o navegador

Aqui seguimos o "minimalismo": não instalamos nenhum ambiente desktop extra (como GNOME/KDE), apenas os drivers de base, o gerenciador de janelas mais leve (Openbox), a ferramenta de ocultar o mouse e o navegador Chromium.

Bash

```
sudo timedatectl set-timezone Asia/Shanghai


sudo apt install net-tools vim fonts-wqy-microhei fonts-wqy-zenhei alsa-utils pulseaudio -y
sudo apt install --no-install-recommends xserver-xorg x11-xserver-utils xinit openbox unclutter -y

wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb
sudo apt install ./google-chrome-stable_current_amd64.deb -y
rm google-chrome-stable_current_amd64.deb

sudo apt purge snapd -y

```

### Passo 2: configurar o login automático sem senha no TTY1 no boot

Para evitar o desconforto de digitar usuário e senha manualmente, modificamos o serviço do systemd para que o sistema faça login automaticamente como `xz` assim que ligar. Aqui usamos um comando de escrita em uma etapa só, contornando completamente problemas de salvamento causados por uso incorreto do `nano` ou do `vi`.

**1. Crie o diretório de configuração:**

Bash

```
sudo mkdir -p /etc/systemd/system/getty@tty1.service.d/
```

**2. Escreva a regra de login automático:**

Bash

```
echo -e "[Service]\nExecStart=\nExecStart=-/sbin/agetty --autologin xz --noclear %I \$TERM" | sudo tee /etc/systemd/system/getty@tty1.service.d/override.conf
```

**3. Recarregue os serviços e defina o target de boot padrão:**

Bash

```
sudo systemctl daemon-reload
sudo systemctl set-default multi-user.target
```

### Passo 3: configurar a inicialização automática da interface gráfica após o login

Após o login automático, o sistema por padrão fica parado em uma linha de comando de fundo preto e texto branco; precisamos configurar um script para que, assim que o login acontecer, o ambiente gráfico X11 inicie imediatamente.

**1. Lógica de inicialização que dispara o `startx`:**

Anexe diretamente o código de disparo ao seu arquivo de configuração pessoal:

Bash

```
cat << 'EOF' >> ~/.bash_profile
if [ -z "$DISPLAY" ] && [ "$(fgconsole)" -eq 1 ]; then
    exec startx
fi
EOF
```

**2. Diga ao `startx` para iniciar o Openbox:**

Bash

```
echo "exec openbox-session" > ~/.xinitrc
```

### Passo 4: configurar o Openbox e o navegador à prova de tudo

Este é o passo mais crucial: desativar a suspensão da tela, ocultar o mouse, travar o navegador em tela cheia e escrever um "loop infinito" para garantir que, se o navegador for fechado acidentalmente, ele renasça instantaneamente.

**1. Crie o diretório de configuração do Openbox:**

Bash

```
mkdir -p ~/.config/openbox
```

**2. Escreva o script de autostart (`autostart`):**

Copie o bloco de código abaixo por inteiro e pressione Enter (isso grava automaticamente todas as regras de proteção no arquivo):

Bash

```
cat << 'EOF' > ~/.config/openbox/autostart
# Desativa a proteção de tela
xset -dpms
xset s noblank
xset s off

# Oculta o mouse
unclutter -idle 0.1 -root &

# Loop infinito iniciando o Chromium (se travar ou for fechado, reinicia em segundos)
while true; do
    google-chrome \
        --kiosk \
        --no-first-run \
        --no-default-browser-check \
        --disable-infobars \
        --disable-session-crashed-bubble \
        --disable-translate \
        --disable-external-intent-requests \
        --autoplay-policy=no-user-gesture-required \
        --use-fake-ui-for-media-stream \
        "https://www.douyin.com"
    sleep 2
done &
EOF
```

**3. Bloqueie o atalho de saída `Alt+F4`:**

Para impedir que alguém conecte um teclado e force o fechamento da janela, removemos os atalhos de sistema padrão do Openbox.

Bash

```
cp /etc/xdg/openbox/rc.xml ~/.config/openbox/
sed -i '/<keybind key="A-F4">/,/<\/keybind>/d' ~/.config/openbox/rc.xml
```

### Passo 5: reinicie e valide o resultado

Se você remover o cabo de rede ou não precisar esperar todas as redes ficarem online, pode desabilitar os serviços de espera de rede para evitar travamento no boot

Bash

```
sudo systemctl mask systemd-networkd-wait-online.service
sudo systemctl mask NetworkManager-wait-online.service
```

Ocultar as mensagens de boot (GRUB)

Bash

```
sudo sed -i 's/GRUB_CMDLINE_LINUX_DEFAULT=.*/GRUB_CMDLINE_LINUX_DEFAULT="quiet loglevel=3 systemd.show_status=false vt.global_cursor_default=0"/g' /etc/default/grub

echo 'GRUB_TIMEOUT_STYLE="hidden"' | sudo tee -a /etc/default/grub
echo 'GRUB_RECORDFAIL_TIMEOUT=0' | sudo tee -a /etc/default/grub

sudo update-grub
```

Defina o som em 100% e reinicie:

Bash

```
amixer -q sset Master 100% unmute
sudo reboot
```

### Implantação do serviço de wake word

Para implantar o serviço de detecção de wake word no equipamento all-in-one, é preciso instalar o ambiente Python, subir os arquivos do projeto, configurar o microfone da Camera e a inicialização automática no boot.

#### 1. Instalar o Miniconda

```bash
wget https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh
bash Miniconda3-latest-Linux-x86_64.sh -b -p $HOME/miniconda3
~/miniconda3/bin/conda init bash
source ~/.bashrc
rm Miniconda3-latest-Linux-x86_64.sh
```


Garanta que, ao fazer login, o ambiente conda seja ativado automaticamente

```bash
if ! grep -q '.bashrc' ~/.bash_profile; then
    cat << 'EOF' >> ~/.bash_profile

if [ -f ~/.bashrc ]; then
    . ~/.bashrc
fi
EOF
fi
```


#### 2. Criar o ambiente virtual Python

```bash
conda create -n test python=3.10 -y
conda activate test
```

Se aparecer o erro "Terms of Service have not been accepted", execute:

```bash
conda tos accept --override-channels --channel https://repo.anaconda.com/pkgs/main
conda tos accept --override-channels --channel https://repo.anaconda.com/pkgs/r
```

#### 3. Subir os arquivos do projeto

Suba o diretório inteiro `main/digital-human/` da máquina de desenvolvimento para o diretório `~/digital-human/` do equipamento all-in-one:

```bash
# Executar na máquina de desenvolvimento (substitua <一体机IP> pelo IP real)
scp -r main/digital-human/ xz@<一体机IP>:~/digital-human/
```

#### 4. Instalar as dependências do sistema

O serviço de wake word precisa das bibliotecas de captura de áudio e dos plugins ALSA/PulseAudio:

```bash
sudo apt install libportaudio2 portaudio19-dev libasound2-plugins -y
```

#### 5. Instalar as dependências Python

```bash
cd ~/digital-human/wakeword_runtime
pip install numpy
pip install -r requirements.txt
```

#### 6. Baixar o modelo de wake word

Os arquivos de modelo não estão incluídos no projeto e precisam ser baixados e configurados à parte; veja a seção "Download do modelo" em [docs/digital-human-wakeword.md](digital-human-wakeword.md).

#### 7. Modificar o script de autostart do Openbox

É preciso adicionar ao autostart a configuração do PulseAudio e do microfone da Camera, e mudar a URL do Chrome para a página de teste.

Primeiro confirme o nome do dispositivo do microfone da Camera no PulseAudio:

```bash
pulseaudio --start
pactl list sources short
```

Encontre a linha que contém `USB_Camera`, anote o nome completo, por exemplo:

```
alsa_input.usb-SN0002_2K_USB_Camera_46435000_P030D00_SN0002-02.mono-fallback
```

Depois sobrescreva o autostart com o conteúdo completo abaixo (substitua `TARGET_MIC` pelo nome real do seu dispositivo):

```bash
cat << 'EOF' > ~/.config/openbox/autostart
# 1. Inicia o serviço de som e aguarda um pouco
pulseaudio --start
sleep 1

# 2. Fixa o microfone da Camera (substitua pelo nome real do seu dispositivo)
TARGET_MIC="alsa_input.usb-SN0002_2K_USB_Camera_46435000_P030D00_SN0002-02.mono-fallback"

# 3. Define como microfone padrão do sistema
pactl set-default-source "$TARGET_MIC"

# 4. Desmuta
pactl set-source-mute "$TARGET_MIC" 0

# 5. Volume em 100%
pactl set-source-volume "$TARGET_MIC" 100%

# --- Configuração do ambiente desktop minimalista e do navegador ---

# Desativa a proteção de tela
xset -dpms
xset s noblank
xset s off

# Oculta o mouse
unclutter -idle 0.1 -root &

# Loop infinito iniciando o navegador (se travar ou for fechado, reinicia em segundos)
while true; do
    google-chrome \
        --kiosk \
        --no-first-run \
        --no-default-browser-check \
        --disable-infobars \
        --disable-session-crashed-bubble \
        --disable-translate \
        --disable-external-intent-requests \
        --autoplay-policy=no-user-gesture-required \
        --use-fake-ui-for-media-stream \
        "http://127.0.0.1:8006/index.html"
    sleep 2
done &
EOF
```

#### 8. Configurar o serviço de wake word para iniciar com o sistema

Crie o arquivo de serviço do systemd para que o serviço de wake word rode automaticamente no boot.

Primeiro confirme o UID do usuário atual:

```bash
id -u $(whoami)
```

Depois substitua, no conteúdo abaixo, o `1000` pelo UID encontrado (normalmente o primeiro usuário é 1000):

```bash
sudo tee /etc/systemd/system/digital-human.service << 'EOF'
[Unit]
Description=Digital Human Runtime
After=network.target sound.target

[Service]
Type=simple
User=xz
Environment=XDG_RUNTIME_DIR=/run/user/1000
Environment=PULSE_SERVER=unix:/run/user/1000/pulse/native
WorkingDirectory=/home/xz/digital-human
ExecStartPre=/bin/sleep 10
ExecStart=/home/xz/miniconda3/envs/test/bin/python start.py
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
```

> **Observações importantes**:
> - `User=xz` — substitua pelo seu nome de usuário real
> - `/run/user/1000` — substitua pelo seu UID real
> - os caminhos em `WorkingDirectory` e `ExecStart` — substitua pelos seus caminhos reais de implantação
> - as variáveis de ambiente PulseAudio em `Environment` **devem ser mantidas**, senão o serviço de wake word e o navegador não conseguem usar o microfone da Camera ao mesmo tempo

Habilite e inicie o serviço:

```bash
sudo systemctl daemon-reload
sudo systemctl enable digital-human
sudo systemctl start digital-human
```

#### 9. Comandos comuns de gerenciamento do serviço

```bash
sudo systemctl start digital-human     # inicia imediatamente
sudo systemctl stop digital-human      # para
sudo systemctl restart digital-human   # reinicia
sudo systemctl status digital-human    # mostra o status
journalctl -u digital-human -f         # acompanha os logs em tempo real
```

