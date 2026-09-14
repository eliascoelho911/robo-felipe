# Console de Gerenciamento (智控台): tutorial de configuração da síntese de voz em fluxo duplo da Volcano (火山双流式语音合成) + clonagem de voz

Este tutorial é dividido em 4 etapas: preparação, configuração, clonagem e uso. Ele descreve principalmente o processo de configuração da síntese de voz em fluxo duplo da Volcano + clonagem de voz por meio do Console de Gerenciamento.

## Etapa 1: Preparação
O superadministrador deve primeiro ativar o serviço do Volcano Engine (火山引擎) com antecedência e obter o App Id e o Access Token. Por padrão, o Volcano Engine oferece gratuitamente um recurso de voz. Esse recurso de voz precisa ser copiado para este projeto.

Se você quiser clonar várias vozes, será preciso comprar e ativar vários recursos de voz. Basta copiar para este projeto o ID de voz (S_xxxxx) de cada recurso de voz e, em seguida, atribuí-los às contas do sistema para uso. Veja abaixo os passos detalhados:

### 1. Ativar o serviço do Volcano Engine
Acesse https://console.volcengine.com/speech/app e crie um aplicativo em Gerenciamento de Aplicativos, marcando o modelo de síntese de voz (语音合成大模型) e o modelo de clonagem de voz (声音复刻大模型).

### 2. Obter o ID do recurso de voz
Acesse https://console.volcengine.com/speech/service/9999 e copie três itens: o App Id, o Access Token e o ID de voz (S_xxxxx). Conforme a imagem:

![Obtendo o recurso de voz](../upstream/docs/images/image-clone-integration-01.png)

## Etapa 2: Configurar o serviço do Volcano Engine

### 1. Preencher a configuração do Volcano Engine

Faça login no Console de Gerenciamento com a conta de superadministrador, clique em "Configuração de Modelos" (模型配置) no topo e, em seguida, clique em "Síntese de Voz" (语音合成) no lado esquerdo da página de configuração de modelos. Pesquise e localize "火山双流式语音合成" (síntese de voz em fluxo duplo da Volcano), clique em modificar, preencha o `App Id` do seu Volcano Engine no campo 【应用ID】 (ID do Aplicativo) e o `Access Token` no campo 【访问令牌】 (Token de Acesso). Em seguida, salve.

### 2. Atribuir o ID do recurso de voz a uma conta do sistema

Faça login no Console de Gerenciamento com a conta de superadministrador, clique em `Dicionário de Parâmetros` (参数字典) no topo e, no menu suspenso, abra a página `Configuração de Funcionalidades do Sistema` (系统功能配置). Nessa página, marque `Clonagem de Voz` (音色克隆) e clique em salvar a configuração. O botão `Clonagem de Voz` aparecerá no menu superior.

Faça login no Console de Gerenciamento com a conta de superadministrador e clique no topo em "Clonagem de Voz" (音色克隆) e "Recursos de Voz" (音色资源).

Clique no botão de adicionar e, em "Nome da Plataforma" (平台名称), selecione "火山双流式语音合成" (síntese de voz em fluxo duplo da Volcano);

Em "ID do Recurso de Voz" (音色资源ID), preencha o ID do recurso de voz (S_xxxxx) do seu Volcano Engine e pressione Enter após inserir;

Em "Conta de Atribuição" (归属账号), selecione a conta do sistema à qual deseja atribuir; você pode atribuir a você mesmo. Em seguida, clique em salvar.

## Etapa 3: Clonagem

Após o login, clique no topo em "Clonagem de Voz" (音色克隆) > "Clonagem de Voz" (音色克隆); se for exibida a mensagem "Sua conta ainda não possui recursos de voz, contate o administrador para que um recurso de voz seja atribuído" (您的账号暂无音色资源请联系管理员分配音色资源), significa que na Etapa 2 você ainda não atribuiu o ID do recurso de voz a esta conta. Nesse caso, volte à Etapa 2 e atribua o recurso de voz à conta correspondente.

Se após o login, ao clicar no topo em "Clonagem de Voz" (音色克隆) > "Clonagem de Voz" (音色克隆), você conseguir ver a lista de vozes correspondente, continue.

Na lista, você verá as vozes correspondentes. Selecione um dos recursos de voz e clique no botão "Enviar Áudio" (上传音频). Depois de enviar, é possível ouvir uma prévia do som ou recortar um trecho. Após confirmar, clique no botão "Enviar Áudio".
![Enviando áudio](../upstream/docs/images/image-clone-integration-02.png)

Depois de enviar o áudio, na lista a voz correspondente passará para o status "Aguardando replicação" (待复刻). Clique no botão "Replicar Agora" (立即复刻). Aguarde de 1 a 2 segundos para receber o resultado.

Se a replicação falhar, passe o mouse sobre o ícone de "mensagem de erro" (错误信息) para ver o motivo da falha.

Se a replicação for bem-sucedida, na lista a voz correspondente passará para o status "Treinamento concluído" (训练成功). Nesse momento, você pode clicar no botão de edição da coluna "Nome da Voz" (声音名称) para renomear o recurso de voz, facilitando a seleção futura.

## Etapa 4: Uso

Clique em "Gerenciamento de Agentes" (智能体管理) no topo, escolha qualquer agente e clique no botão "Configurar Papel" (配置角色).

Em Síntese de Voz (TTS), selecione "火山双流式语音合成" (síntese de voz em fluxo duplo da Volcano). Na lista, encontre o recurso de voz cujo nome contenha "克隆音色" (voz clonada), conforme a imagem; selecione-o e clique em salvar.
![Selecionando a voz](../upstream/docs/images/image-clone-integration-03.png)

Em seguida, basta acordar o Xiaozhi e conversar com ele.
