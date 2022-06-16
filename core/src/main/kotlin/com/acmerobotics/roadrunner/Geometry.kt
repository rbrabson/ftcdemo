package com.acmerobotics.roadrunner

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class Position2(val x: Double, val y: Double) {
    operator fun plus(v: Vector2) = Position2(x + v.x, y + v.y)

    operator fun minus(other: Position2) = Vector2(x - other.x, y - other.y)
}

data class Position2Dual<Param>(val x: DualNum<Param>, val y: DualNum<Param>) {
    companion object {
        fun <Param> constant(p: Position2, n: Int) = Position2Dual<Param>(
                DualNum.constant(p.x, n), DualNum.constant(p.y, n)
        )
    }

    operator fun minus(other: Position2Dual<Param>) = Vector2Dual(x - other.x, y - other.y)

    fun free() = Vector2Dual(x, y)

    // TODO: this won't be a valid rotation without arc length param
    fun tangent() = Rotation2Dual(x.drop(1), y.drop(1))
    fun tangentVec() = Vector2Dual(x.drop(1), y.drop(1))

    fun <NewParam> reparam(oldParam: DualNum<NewParam>) =
            Position2Dual(x.reparam(oldParam), y.reparam(oldParam))

    fun value() = Position2(x.value(), y.value())
}

data class Vector2(val x: Double, val y: Double) {
    operator fun plus(other: Vector2) = Vector2(x + other.x, y + other.y)
    operator fun minus(other: Vector2) = Vector2(x - other.x, y - other.y)
    operator fun unaryMinus() = Vector2(-x, -y)

    operator fun times(other: Double) = Vector2(x * other, y * other)
    operator fun div(other: Double) = Vector2(x / other, y / other)

    infix fun dot(other: Vector2) = x * other.x + y * other.y
    fun sqrNorm() = this dot this
    fun norm() = sqrt(sqrNorm())

    fun bind() = Position2(x, y)
}

data class Vector2Dual<Param>(val x: DualNum<Param>, val y: DualNum<Param>) {
    companion object {
        fun <Param> constant(v: Vector2, n: Int) =
            Vector2Dual<Param>(DualNum.constant(v.x, n), DualNum.constant(v.y, n))
    }

    operator fun plus(other: Vector2Dual<Param>) = Vector2Dual(x + other.x, y + other.y)
    operator fun unaryMinus() = Vector2Dual(-x, -y)

    operator fun div(other: Double) = Vector2Dual(x / other, y / other)

    operator fun plus(other: Position2) = Position2Dual(x + other.x, y + other.y)

    operator fun plus(other: Vector2) = Vector2Dual(x + other.x, y + other.y)

    infix fun dot(other: Vector2Dual<Param>) = x * other.x + y * other.y
    fun sqrNorm() = this dot this
    fun norm() = sqrNorm().sqrt()

    fun drop(n: Int) = Vector2Dual(x.drop(n), y.drop(n))
    fun value() = Vector2(x.value(), y.value())

    fun <NewParam> reparam(oldParam: DualNum<NewParam>) =
            Vector2Dual(x.reparam(oldParam), y.reparam(oldParam))

    fun bind() = Position2Dual(x, y)
}


data class Rotation2(val real: Double, val imag: Double) {
    companion object {
        fun exp(theta: Double) = Rotation2(cos(theta), sin(theta))
    }

    operator fun plus(x: Double) = this * exp(x)

    operator fun times(vector: Vector2) = Vector2(
        real * vector.x - imag * vector.y,
        imag * vector.x + real * vector.y
    )

    operator fun times(other: Rotation2) = Rotation2(
        real * other.real - imag * other.imag,
        real * other.imag + imag * other.real
    )

    fun inverse() = Rotation2(real, -imag)

    operator fun minus(other: Rotation2) = (other.inverse() * this).log()

    fun vec() = Vector2(real, imag)

    fun log() = atan2(imag, real)
}

data class Rotation2Dual<Param>(val real: DualNum<Param>, val imag: DualNum<Param>) {
    init {
        require(real.size == imag.size)
        require(real.size <= 3)
    }

    companion object {
        fun <Param> exp(theta: DualNum<Param>) = Rotation2Dual(theta.cos(), theta.sin())

        fun <Param> constant(r: Rotation2, n: Int) =
            Rotation2Dual<Param>(DualNum.constant(r.real, n), DualNum.constant(r.imag, n))
    }

    operator fun plus(x: Double) = this * Rotation2.exp(x)

    operator fun plus(n: DualNum<Param>) = this * exp(n)

    operator fun times(vector: Vector2Dual<Param>) = Vector2Dual(
            real * vector.x - imag * vector.y,
            imag * vector.x + real * vector.y
    )

    operator fun times(vector: Vector2) = Vector2Dual(
            real * vector.x - imag * vector.y,
            imag * vector.x + real * vector.y
    )

    operator fun times(other: Rotation2Dual<Param>) = Rotation2Dual(
            real * other.real - imag * other.imag,
            real * other.imag + imag * other.real
    )

    operator fun times(other: Rotation2) = Rotation2Dual(
            real * other.real - imag * other.imag,
            real * other.imag + imag * other.real
    )

    fun inverse() = Rotation2Dual(real, -imag)

    fun value() = Rotation2(real.value(), imag.value())

    fun <NewParam> reparam(oldParam: DualNum<NewParam>) =
            Rotation2Dual(real.reparam(oldParam), imag.reparam(oldParam))

    operator fun minus(other: Rotation2Dual<Param>) = (other.inverse() * this).log()

    // TODO: I'd like to somehow merge this with velocity()
    fun log() = DualNum<Param>(DoubleArray(size) {
        when (it) {
            0 -> atan2(imag[0], real[0])
            1 -> real[0] * imag[1] - imag[0] * real[1]
            2 -> real[0] * imag[2] - imag[0] * real[2]
            // ensured by init{} check
            else -> throw AssertionError()
        }
    })

    // derivative of atan2 under unit norm assumption
    fun velocity() = real * imag.drop(1) - imag * real.drop(1)

    val size get() = real.size
}

data class Transform2(
        val translation: Vector2,
        val rotation: Rotation2,
) {
    companion object {
        // see (133), (134) in https://ethaneade.com/lie.pdf
        private fun entries(theta: Double) : Pair<Double, Double> {
            val u = theta + epsCopySign(theta)
            return Pair(
                sin(u) / u,
                (1.0 - cos(u)) / u
            )
        }

        fun exp(incr: Twist2Incr): Transform2 {
            val rotation = Rotation2.exp(incr.rotIncr)

            val (A, B) = entries(incr.rotIncr)
            val translation = Vector2(
                    A * incr.transIncr.x - B * incr.transIncr.y,
                    B * incr.transIncr.x + A * incr.transIncr.y
            )

            return Transform2(translation, rotation)
        }
    }

    operator fun plus(t: Twist2Incr) = this * exp(t)

    operator fun times(other: Transform2) =
        Transform2(rotation * other.translation + translation, rotation * other.rotation)

    operator fun times(other: Vector2) = rotation * other + translation

    operator fun times(other: Twist2) = Twist2(rotation * other.transVel, other.rotVel)

    fun inverse() = Transform2(rotation.inverse() * -translation, rotation.inverse())

    fun log(): Twist2Incr {
        val theta = rotation.log()

        val (A, B) = entries(theta)
        val denom = Vector2(A, B).sqrNorm()

        val (x, y) = translation
        return Twist2Incr(
                Vector2(
                    (A * x + B * y) / denom,
                    (-B * x + A * y) / denom,
                ),
                theta,
        )
    }

    operator fun minus(other: Transform2) = (other.inverse() * this).log()
}

data class Transform2Error(val transError: Vector2, val rotError: Double)

// NOTE: SE(2) minus mixes the frame orientations, and we need it purely in the actual frame
// TODO: does this need its own type?
fun localError(targetPose: Transform2, actualPose: Transform2): Transform2Error {
    val transErrorWorld = targetPose.translation - actualPose.translation
    val rotError = targetPose.rotation - actualPose.rotation
    return Transform2Error(actualPose.rotation.inverse() * transErrorWorld, rotError)
}

data class Transform2Dual<Param>(
        val translation: Vector2Dual<Param>,
        val rotation: Rotation2Dual<Param>,
) {
    companion object {
        fun <Param> constant(t: Transform2, n: Int) =
            Transform2Dual<Param>(Vector2Dual.constant(t.translation, n), Rotation2Dual.constant(t.rotation, n))
    }

    operator fun times(other: Transform2Dual<Param>) =
            Transform2Dual(rotation * other.translation + translation, rotation * other.rotation)

    operator fun times(other: Transform2) =
            Transform2Dual(rotation * other.translation + translation, rotation * other.rotation)

    operator fun plus(other: Twist2Incr) = this * Transform2.exp(other)

    fun value() = Transform2(translation.value(), rotation.value())

    fun inverse() = Transform2Dual(rotation.inverse() * -translation, rotation.inverse())

    operator fun times(other: Twist2Dual<Param>) = Twist2Dual(rotation * other.transVel, other.rotVel)

    fun velocity() = Twist2Dual(translation.drop(1), rotation.velocity())

    fun <NewParam> reparam(oldParam: DualNum<NewParam>) =
            Transform2Dual(translation.reparam(oldParam), rotation.reparam(oldParam))
}

data class Twist2(val transVel: Vector2, val rotVel: Double)

data class Twist2Dual<Param>(val transVel: Vector2Dual<Param>, val rotVel: DualNum<Param>) {
    companion object {
        fun <Param> constant(t: Twist2, n: Int) =
            Twist2Dual<Param>(Vector2Dual.constant(t.transVel, n), DualNum.constant(t.rotVel, n))
    }

    operator fun plus(other: Twist2) = Twist2Dual(transVel + other.transVel, rotVel + other.rotVel)

    fun constant() = Twist2(transVel.value(), rotVel.value())
}

data class Twist2Incr(val transIncr: Vector2, val rotIncr: Double)
