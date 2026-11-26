package dev.jandoant.fuel.core.model

import java.time.LocalDate
import java.time.LocalTime

val D0: LocalDate = LocalDate.of(2026, 1, 5) // a Monday

fun day(n: Int): LocalDate = D0.plusDays(n.toLong())

fun reading(n: Int, kg: Double, hour: Int = 7) = WeightReading(day(n).atTime(LocalTime.of(hour, 0)), kg)

/** Weight that moves linearly by [kgPerDay] with a deterministic ±[noise] zig-zag. */
fun linearWeights(days: IntRange, startKg: Double, kgPerDay: Double, noise: Double = 0.0): List<DailyWeight> =
    days.map { DailyWeight(day(it), startKg + kgPerDay * it + if (it % 2 == 0) noise else -noise) }

fun List<DailyWeight>.asReadings() = map { WeightReading(it.date.atTime(7, 0), it.kg) }

fun intake(days: IntRange, kcal: Double): Map<LocalDate, Double> = days.associate { day(it) to kcal }
