package me.sweetll.tucao.extension

import io.reactivex.subjects.BehaviorSubject

class Variable<T: Any>(defaultValue: T) {
    val stream: BehaviorSubject<T> = BehaviorSubject.createDefault(defaultValue)
    var value: T = defaultValue
        set(newValue) {
            field = newValue
            stream.onNext(newValue)
        }
}