# Guia de integração do SMS da Alibaba Cloud (Aliyun)

Faça login no console da Alibaba Cloud e acesse a página "Serviço de SMS" (短信服务): https://dysms.console.aliyun.com/overview

## Passo 1 Adicionar assinatura
![Passo](../upstream/docs/images/alisms/sms-01.png)
![Passo](../upstream/docs/images/alisms/sms-02.png)

Com os passos acima, você obterá a assinatura (签名); insira-a no parâmetro do Console de Gerenciamento (智控台) `aliyun.sms.sign_name`

## Passo 2 Adicionar template
![Passo](../upstream/docs/images/alisms/sms-11.png)

Com os passos acima, você obterá o código do template (template code); insira-o no parâmetro do Console de Gerenciamento (智控台) `aliyun.sms.sms_code_template_code`

Atenção: a assinatura leva 7 dias úteis para ficar pronta; o envio só funcionará depois que o registro for aprovado junto à operadora.

Atenção: a assinatura leva 7 dias úteis para ficar pronta; o envio só funcionará depois que o registro for aprovado junto à operadora.

Atenção: a assinatura leva 7 dias úteis para ficar pronta; o envio só funcionará depois que o registro for aprovado junto à operadora.

Você pode esperar a aprovação do registro antes de continuar com os próximos passos.

## Passo 3 Criar a conta de SMS e habilitar as permissões

Faça login no console da Alibaba Cloud e acesse a página "Controle de Acesso" (访问控制): https://ram.console.aliyun.com/overview?activeTab=overview

![Passo](../upstream/docs/images/alisms/sms-21.png)
![Passo](../upstream/docs/images/alisms/sms-22.png)
![Passo](../upstream/docs/images/alisms/sms-23.png)
![Passo](../upstream/docs/images/alisms/sms-24.png)
![Passo](../upstream/docs/images/alisms/sms-25.png)

Com os passos acima, você obterá o access_key_id e o access_key_secret; insira-os nos parâmetros do Console de Gerenciamento (智控台) `aliyun.sms.access_key_id` e `aliyun.sms.access_key_secret`
## Passo 4 Ativar o registro por celular

1. Normalmente, depois de preencher todas as informações acima, você verá o resultado abaixo; se não vir, pode ter faltado algum passo

![Passo](../upstream/docs/images/alisms/sms-31.png)

2. Ative a permissão de registro para usuários não administradores, definindo o parâmetro `server.allow_user_register` como `true`

3. Ative a função de registro por celular, definindo o parâmetro `server.enable_mobile_register` como `true`
![Passo](../upstream/docs/images/alisms/sms-32.png)
