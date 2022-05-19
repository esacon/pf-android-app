package com.example.lunghealth.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lunghealth.MainActivity
import com.example.lunghealth.R
import com.google.android.material.textfield.TextInputEditText
import kotlinx.android.synthetic.main.activity_login.*
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import org.json.JSONTokener
import java.util.regex.Pattern


//const val SERVER_URL: String = "https://api-breath.herokuapp.com/login"
const val SERVER_URL: String = "https://api-breath.herokuapp.com/"

class LoginActivity : AppCompatActivity() {

    // Login EditText
    private lateinit var usernameEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText

    // SignUp EditText
    private lateinit var nameEditText: TextInputEditText
    private lateinit var emailEditText: TextInputEditText
    private lateinit var usrnameEditText: TextInputEditText
    private lateinit var ageEditText: TextInputEditText
    private lateinit var passEditText: TextInputEditText
    private lateinit var confirmPassEditText: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Login EditText
        usernameEditText = findViewById(R.id.username)
        passwordEditText = findViewById(R.id.password)

        // SignUp EditText
        nameEditText = findViewById(R.id.sname)
        emailEditText = findViewById(R.id.semail)
        usrnameEditText = findViewById(R.id.susername)
        ageEditText = findViewById(R.id.sedad)
        passEditText = findViewById(R.id.spassword)
        confirmPassEditText = findViewById(R.id.sconfirm_password)

        signUp.setOnClickListener {
            signUp.background = resources.getDrawable(R.drawable.switch_trcks, null)
            signUp.setTextColor(resources.getColor(R.color.white, null))
            logIn.background = null
            signUpLayout.visibility = View.VISIBLE
            signUpButton.visibility = View.VISIBLE
            logInLayout.visibility = View.GONE
            logInButton.visibility = View.GONE
            logIn.setTextColor(resources.getColor(R.color.mainBlue, null))
            doctoImage.visibility = View.INVISIBLE
        }

        logIn.setOnClickListener {
            signUp.background = null
            signUp.setTextColor(resources.getColor(R.color.mainBlue, null))
            logIn.background = resources.getDrawable(R.drawable.switch_trcks, null)
            signUpLayout.visibility = View.GONE
            signUpButton.visibility = View.GONE
            logInLayout.visibility = View.VISIBLE
            logInButton.visibility = View.VISIBLE
            logIn.setTextColor(resources.getColor(R.color.white, null))
            doctoImage.visibility = View.VISIBLE
        }

        logInButton.setOnClickListener {
            logInValidation()
        }

        signUpButton.setOnClickListener {
            signUpValidation()
        }
    }

    private fun doLogin(username: String, password: String) {
        val formBody: FormBody = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .build()
        Thread {
            GlobalScope.launch(Dispatchers.IO) {
                val jsonObject = async { postDataServer("user/login", formBody) }.await()
                val message = jsonObject?.getString("login")
                if (message != null) {
                    Log.i("Info response", message)
                    if (message == "true") {
                        showToast("Sesión iniciada", visibleLong = false)
                        val user_id = jsonObject.getString("user_id")
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        val intent: Intent = Intent(this@LoginActivity, MainActivity::class.java)
                        intent.putExtra("user_id", user_id)
                        startActivity(intent)
                    } else {
                        showToast("Usuario o contraseña incorrectos.", visibleLong = false)
                    }
                }
            }
        }.start()
    }

    private fun doSignUp(
        name: String,
        email: String,
        username: String,
        age: String,
        password: String,
        confirmPassword: String
    ) {
        val formBody: FormBody = FormBody.Builder()
            .add("name", name)
            .add("email", email)
            .add("username", username)
            .add("age", age)
            .add("password", password)
            .add("confirm_password", confirmPassword)
            .build()
        Thread {
            GlobalScope.launch(Dispatchers.IO) {
                val jsonObject = async { postDataServer("user", formBody) }.await()
                val message = jsonObject?.getString("login")
                if (message != null) {
                    Log.i("Info response", message)
                    if (message == "true") {
                        showToast("Usuario registrado", visibleLong = false)
                        val user_id = jsonObject.getString("user_id")
                        val intent: Intent = Intent(this@LoginActivity, MainActivity::class.java)
                        intent.putExtra("user_id", user_id)
                        startActivity(intent)
                    } else {
                        showToast("No se pudo registrar el usuario", visibleLong = false)
                    }
                }
            }
        }.start()
    }

    private fun logInValidation() {
        val username: String = usernameEditText.text.toString()
        val password: String = passwordEditText.text.toString()
        if (username == "" || password == "") {
            showToast("Por favor revise los campos.", visibleLong = false)
            return
        }
        doLogin(username, password)
    }

    private fun signUpValidation() {
        val name: String = nameEditText.text.toString().trim()
        val email: String = emailEditText.text.toString().trim()
        val username: String = usrnameEditText.text.toString().trim()
        val age: String = ageEditText.text.toString()
        val password: String = passEditText.text.toString().trim()
        val confirmPassword: String = confirmPassEditText.text.toString().trim()
        if (name == "" || email == "" || age == "" || username == "" || password == "" || confirmPassword == "") {
            showToast("Por favor revise los campos.", visibleLong = false)
            return
        }
        if (confirmPassword != password) {
            showToast("Las contraseñas no coinciden.", visibleLong = false)
            return
        }
        if (Integer.parseInt(age) > 110 || Integer.parseInt(age) < 1) {
            showToast("Ingrese una edad válida.", visibleLong = false)
            return
        }
        if (!isEmailValid(email)) {
            showToast("Ingrese un email válido.", visibleLong = false)
            return
        }
        if (!isPasswordValid(password)) {
            showToast("Ingrese una contraseña válida.", visibleLong = false)
            return
        }
        if (!isUsernameValid(username)) {
            showToast("Ingrese un usuario válido.", visibleLong = false)
            return
        }
        doSignUp(name, email, username, age, password, confirmPassword)
    }

    private suspend fun postDataServer(ruta: String, requestBody: RequestBody): JSONObject? {
        val client = OkHttpClient()

        try {
            val url: String = "$SERVER_URL$ruta"
            Log.i("URL", url)
            val request: Request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("content-type", "multipart/form-data;").build()

            val response: Response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Log.d("Data upload", "success")
                val responseBodyString = response.body!!.string()
                val jsonObject = JSONTokener(responseBodyString).nextValue() as JSONObject
                return jsonObject
            } else {
                Log.e("Data upload", "failed")
                Log.e("Response error", response.toString())
            }
            response.close()
        } catch (e: Exception) {
            Log.e("Post failed", e.stackTraceToString())
        }
        return null
    }

    private fun showToast(message: String, visibleLong: Boolean) {
        this.runOnUiThread {
            if (visibleLong) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isEmailValid(email: String): Boolean {
        val emailRegex: Pattern = Pattern.compile(
            "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?",
            Pattern.CASE_INSENSITIVE
        )
        return emailRegex.matcher(email).matches()
    }

    private fun isPasswordValid(password: String): Boolean {
        val passRegex: Pattern = Pattern.compile(
            "(?=.*[0-9])(?=.*[a-z])(?=\\S+$).{8,}",
            Pattern.CASE_INSENSITIVE
        )
        return passRegex.matcher(password).matches()
    }

    private fun isUsernameValid(username: String): Boolean {
        val passRegex: Pattern = Pattern.compile(
            "(?=.*[a-z])(?=\\S+$).{5,}",
            Pattern.CASE_INSENSITIVE
        )
        return passRegex.matcher(username).matches()
    }
}