#include <ACB_Biped_Robot_WiFi.h>
#include <WiFi.h>

#define FIRMWARE_VERSION "20240814 V1.0"

ACB_Biped_Robot_WiFi Biped_Robot;

const char* ssid = "Biped_Robot";
const char* password = "12345678";

/*
                  ---------------
                  |     O   O     |
                  |---------------|
  port4  YR 18==>  |             | <== YL 5 port1
                  ---------------
                      ||     ||
                      ||     ||
  port3  RR 17==> ------   ------ <== RL 16 port2
                  |-----   -----|
*/

void setup() {
  Serial.begin(115200);

  Biped_Robot.myservo_init(5, 16, 17, 18);
  Biped_Robot.Ultrasonic_Init();

  WiFi.setTxPower(WIFI_POWER_19_5dBm);
  WiFi.mode(WIFI_AP);
  WiFi.softAP(ssid, password, 9);
  IPAddress myIP = WiFi.softAPIP();
  Serial.print("AP IP address: ");
  Serial.println(myIP);

  Biped_Robot.stop();
  Biped_Robot.startWebServer();

  Serial.println();
  Serial.println("========================================");
  Serial.println("  Robo Bipede ACEBOTT - Pronto!");
  Serial.println("========================================");
  Serial.println("WiFi: Biped_Robot  | Senha: 12345678");
  Serial.println("Web:  http://192.168.4.1");
  Serial.println();
  Serial.println("Controle por Serial (115200):");
  Serial.println("  f  -> frente      b  -> tras");
  Serial.println("  l  -> esquerda    r  -> direita");
  Serial.println("  s  -> parar");
  Serial.println("  dance  -> dancar   kick   -> chute esq");
  Serial.println("  rkick  -> chute dir sprint -> correr");
  Serial.println("  ltilt  -> inclinar esq  rtilt -> inclinar dir");
  Serial.println("  lstamp -> pisar esq   rstamp-> pisar dir");
  Serial.println("  lankle -> tornozelo esq rankle-> tornozelo dir");
  Serial.println("  follow -> seguir    avoid  -> desviar");
  Serial.println("========================================");
}

void dispatch(int code) {
  Biped_Robot.val = code;
}

void loop() {
  handleSerial();

  int v = Biped_Robot.val;

  if (v == 1) {
    Biped_Robot.forward();
  } else if (v == 2) {
    Biped_Robot.backward();
  } else if (v == 3) {
    Biped_Robot.leftward();
  } else if (v == 4) {
    Biped_Robot.rightward();
  } else if (v == 8) {
    Biped_Robot.stop();
  } else if (v == 10) {
    Biped_Robot.sprint();
    Biped_Robot.val = 8;
  } else if (v == 11) {
    Biped_Robot.left_kick();
  } else if (v == 12) {
    Biped_Robot.right_kick();
  } else if (v == 13) {
    Biped_Robot.left_tilt();
    Biped_Robot.val = 8;
  } else if (v == 14) {
    Biped_Robot.right_tilt();
    Biped_Robot.val = 8;
  } else if (v == 15) {
    Biped_Robot.left_stamp();
  } else if (v == 16) {
    Biped_Robot.dance();
    Biped_Robot.val = 8;
  } else if (v == 17) {
    Biped_Robot.avoid();
  } else if (v == 18) {
    Biped_Robot.follow();
  } else if (v == 19) {
    Biped_Robot.left_ankles();
    Biped_Robot.val = 8;
  } else if (v == 20) {
    Biped_Robot.right_stamp();
    Biped_Robot.val = 8;
  } else if (v == 21) {
    Biped_Robot.right_ankles();
    Biped_Robot.val = 8;
  }
}

void handleSerial() {
  if (!Serial.available()) return;

  String input = Serial.readStringUntil('\n');
  input.trim();
  input.toLowerCase();
  Serial.print("> ");
  Serial.println(input);

  if (input == "f" || input == "forward") {
    dispatch(1);
  } else if (input == "b" || input == "backward") {
    dispatch(2);
  } else if (input == "l" || input == "left") {
    dispatch(3);
  } else if (input == "r" || input == "right") {
    dispatch(4);
  } else if (input == "s" || input == "stop") {
    dispatch(8);
  } else if (input == "sprint" || input == "run") {
    dispatch(10);
  } else if (input == "kick" || input == "lkick") {
    dispatch(11);
  } else if (input == "rkick") {
    dispatch(12);
  } else if (input == "ltilt") {
    dispatch(13);
  } else if (input == "rtilt") {
    dispatch(14);
  } else if (input == "lstamp") {
    dispatch(15);
  } else if (input == "dance") {
    dispatch(16);
  } else if (input == "avoid") {
    dispatch(17);
  } else if (input == "follow") {
    dispatch(18);
  } else if (input == "lankle") {
    dispatch(19);
  } else if (input == "rstamp") {
    dispatch(20);
  } else if (input == "rankle") {
    dispatch(21);
  } else if (input == "help") {
    Serial.println("Comandos: f b l r s dance kick rkick");
    Serial.println("          sprint ltilt rtilt lstamp rstamp");
    Serial.println("          lankle rankle follow avoid");
  } else if (input.length() > 0) {
    Serial.println("Comando desconhecido. Digite 'help'.");
  }
}
