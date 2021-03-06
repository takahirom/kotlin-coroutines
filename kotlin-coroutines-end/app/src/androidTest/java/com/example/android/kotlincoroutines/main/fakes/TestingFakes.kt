package com.example.android.kotlincoroutines.main.fakes

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import com.example.android.kotlincoroutines.main.MainNetwork
import com.example.android.kotlincoroutines.main.Title
import com.example.android.kotlincoroutines.main.TitleDao
import com.example.android.kotlincoroutines.util.FakeNetworkCall
import com.example.android.kotlincoroutines.util.FakeNetworkException
import com.google.common.truth.Truth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Fake [TitleDao] for use in tests.
 */
class TitleDaoFake(var titleToReturn: String) : TitleDao {
    val inserted = mutableListOf<String>()

    /**
     * This is used to signal an element has been inserted.
     */
    private var nextInsertion: CompletableDeferred<String>? = null

    /**
     * Protect concurrent access to inserted and nextInsertion
     *
     * Because [insertTitle] will always be called by a background thread.
     */
    private val mutex = Mutex()

    override fun insertTitle(title: Title) {
        runBlocking {
            mutex.withLock {
                inserted += title.title
                // complete the waiting deferred
                nextInsertion?.complete(title.title)
            }
        }
    }

    override fun loadTitle(): LiveData<Title> {
        return MutableLiveData<Title>().apply {
            value = Title(titleToReturn)
        }
    }

    /**
     * Assertion that the next element inserted has a title of expected
     *
     * If the element was previously inserted and is currently the most recent element
     * this assertion will also match. This allows tests to avoid synchronizing calls to insert
     * with calls to assertNextInsert.
     *
     * @param expected the value to match
     * @param timeout duration to wait
     * @param unit timeunit
     */
    fun assertNextInsert(expected: String, timeout: Long = 2, unit: TimeUnit = SECONDS) {
        runBlocking {
            val completableDeferred = CompletableDeferred<String>()
            mutex.withLock {
                // first check if the last element is already expected
                if (inserted.isNotEmpty() && inserted.last() == expected) {
                    return@runBlocking
                }

                // if not, setup a deferred to get notified of next insertion
                nextInsertion = completableDeferred
            }

            // wait for the next insertion to complete the deferred
            try {
                withTimeout(timeout, unit) {
                    val next = completableDeferred.await()
                    Truth.assertThat(next).isEqualTo(expected)
                }
            } catch (ex: TimeoutCancellationException) {
                // generate a nice stack trace
                Truth.assertThat(ex).isEqualTo(expected)
            }

        }
    }
}

/**
 * Testing Fake implementation of MainNetwork
 */
class MainNetworkFake(var call: FakeNetworkCall<String> = makeSuccessCall("title")) : MainNetwork {
    override fun fetchNewWelcome(): FakeNetworkCall<String> {
        return call
    }
}

/**
 * Make a fake successful network result
 *
 * @param result result to return
 */
fun <T> makeSuccessCall(result: T) = FakeNetworkCall<T>().apply {
    onSuccess(result)
}

/**
 * Make a fake failed network call
 *
 * @param throwable error to wrap
 */
fun makeFailureCall(throwable: FakeNetworkException) = FakeNetworkCall<String>().apply {
    onError(throwable)
}
