package tech.fakhrylinux.rxjavasignupscreen

import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import com.jakewharton.rxbinding2.widget.RxTextView
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import tech.fakhrylinux.rxjavasignupscreen.databinding.ActivityMainBinding
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Respond to text change events in enterEmail
        RxTextView.afterTextChangeEvents(binding.enterEmail)
            // Skip enterEmail's initial, empty state
            .skipInitialValue()
            // Transform the data being emitted
            .map {
                binding.emailError.error = null
                // Convert the user input to a String
                it.view().text.toString()
            }
            // Ignore all emissions that occur within a 400 millisecond
            .debounce(
                100,
                // Make sure we're in Android's main UI thread
                TimeUnit.MILLISECONDS
            ).observeOn(AndroidSchedulers.mainThread())
            // Apply the validateEmailAddress transformation function
            .compose(validateEmailAddress)
            // Apply the retryWhenError transformation function
            .compose(retryWhenError {
                binding.passwordError.error = it.message
            })
            .subscribe()

        //Rinse and repeat for the enterPassword EditText//
        RxTextView.afterTextChangeEvents(binding.enterPassword)
            .skipInitialValue()
            .map {
                binding.passwordError.error = null
                it.view().text.toString()
            }
            .debounce(400, TimeUnit.MILLISECONDS).observeOn(AndroidSchedulers.mainThread())
            .compose(validatePassword)
            .compose(retryWhenError {
                binding.passwordError.error = it.message
            })
            .subscribe()
    }
}

// If the app encounters an error, then try again
private inline fun retryWhenError(crossinline onError: (ex: Throwable) -> Unit): ObservableTransformer<String, String> =
    ObservableTransformer { observable ->
        observable.retryWhen { errors ->
            // Use the flatmap() operator to flatten all emissions into a single Observable
            errors.flatMap {
                onError(it)
                Observable.just("")
            }
        }
    }

//Define our ObservableTransformer and specify that the input and output must be a string//
private val validatePassword = ObservableTransformer<String, String> { observable ->
    observable.flatMap {
        Observable.just(it).map { it.trim() }
            // Only allow passwords that are at least 7 characters long
            .filter { it.length > 7 }
            // If the password is less than 7 characters, then throw an error
            .singleOrError()
            // If an error occurs.....
            .onErrorResumeNext {
                if (it is NoSuchElementException) {
                    // Display the following message in the passwordError TextInputLayout
                    Single.error(Exception("Your password must be 7 characters or more"))
                } else {
                    Single.error(it)
                }
            }
            .toObservable()
    }
}

// Define an ObservableTransformer, where we'll perform the email validation
private val validateEmailAddress = ObservableTransformer<String, String> { observable ->
    observable.flatMap {
        Observable.just(it).map { it.trim() }
            // Check whether the user input matches Android's email pattern
            .filter {
                Patterns.EMAIL_ADDRESS.matcher(it).matches()
            }
            // If the user's input doesn't match the email pattern, then throw an error
            .singleOrError()
            .onErrorResumeNext {
                if (it is NoSuchElementException) {
                    Single.error(Exception("Please enter a valid email address"))
                } else {
                    Single.error(it)
                }
            }
            .toObservable()
    }
}
