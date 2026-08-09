package com.example.robofelipe.data

enum class RobotCommand(val code: Int, val label: String) {
    FORWARD(1, "Frente"),
    BACKWARD(2, "Trás"),
    LEFT(3, "Esquerda"),
    RIGHT(4, "Direita"),
    STOP(8, "Parar"),
    SPRINT(10, "Correr"),
    LEFT_KICK(11, "Chute Esq."),
    RIGHT_KICK(12, "Chute Dir."),
    LEFT_TILT(13, "Inclinar Esq."),
    RIGHT_TILT(14, "Inclinar Dir."),
    LEFT_STAMP(15, "Pisar Esq."),
    DANCE(16, "Dançar"),
    AVOID(17, "Desviar"),
    FOLLOW(18, "Seguir"),
    LEFT_ANKLES(19, "Tornozelo Esq."),
    RIGHT_STAMP(20, "Pisar Dir."),
    RIGHT_ANKLES(21, "Tornozelo Dir.");
}
