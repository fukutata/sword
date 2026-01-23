package com.fukutata.sword

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var gameView: GameView
    private lateinit var enemyHP: ProgressBar
    private lateinit var txtResult: TextView
    private lateinit var txtLife: TextView
    private lateinit var txtEnemyName: TextView
    private lateinit var resultLayout: View
    private lateinit var titleLayout: View
    private lateinit var selectLayout: View
    private lateinit var skillSelectLayout: View
    private lateinit var uiContainer: View
    private lateinit var controllerLayout: View
    private lateinit var btnRetry: Button

    private lateinit var btnDragon: Button
    private lateinit var btnGolem: Button
    private lateinit var btnBeholder: Button
    private lateinit var btnDemonKing: Button

    private val handler = Handler(Looper.getMainLooper())
    private var enemyTimer: Timer? = null
    private val defeatedEnemies = mutableSetOf<EnemyType>()

    private var mediaPlayer: MediaPlayer? = null
    private var sePlayer: MediaPlayer? = null

    private var lastInput = ""
    private var commandTimer: Long = 0
    
    private var guardStartTime: Long = 0
    private val guardBreakLimit = 3000L // 3秒

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gameView = findViewById(R.id.gameView)
        enemyHP = findViewById(R.id.enemyHP)
        txtResult = findViewById(R.id.txtResult)
        txtLife = findViewById(R.id.txtPlayerLife)
        txtEnemyName = findViewById(R.id.txtEnemyName)
        resultLayout = findViewById(R.id.resultLayout)
        titleLayout = findViewById(R.id.titleLayout)
        selectLayout = findViewById(R.id.selectLayout)
        skillSelectLayout = findViewById(R.id.skillSelectLayout)
        uiContainer = findViewById(R.id.uiContainer)
        controllerLayout = findViewById(R.id.controllerLayout)
        btnRetry = findViewById(R.id.btnRetry)

        btnDragon = findViewById(R.id.btnSelectDragon)
        btnGolem = findViewById(R.id.btnSelectGolem)
        btnBeholder = findViewById(R.id.btnSelectBeholder)
        btnDemonKing = findViewById(R.id.btnSelectDemonKing)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            titleLayout.visibility = View.GONE
            showSelectionScreen()
        }

        btnDragon.setOnClickListener { startStage(EnemyType.DRAGON) }
        btnGolem.setOnClickListener { startStage(EnemyType.GOLEM) }
        btnBeholder.setOnClickListener { startStage(EnemyType.BEHOLDER) }
        btnDemonKing.setOnClickListener { startStage(EnemyType.DEMON_KING) }

        setupController()

        findViewById<Button>(R.id.btnSkillDoubleJump).setOnClickListener { learnSkill(1) }
        findViewById<Button>(R.id.btnSkillDiveAttack).setOnClickListener { learnSkill(2) }
        findViewById<Button>(R.id.btnSkillHadoken).setOnClickListener { learnSkill(3) }

        findViewById<Button>(R.id.btnLight).setOnClickListener { performAttack(false) }
        findViewById<Button>(R.id.btnHeavy).setOnClickListener { performAttack(true) }
        
        // 防御ボタン
        findViewById<Button>(R.id.btnGuard).setOnTouchListener { _, event ->
            if (gameView.isGameOver || gameView.isReadyGo || gameView.isBossEntering) return@setOnTouchListener false
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (gameView.playerState == PlayerState.IDLE) {
                        gameView.playerState = PlayerState.GUARD
                        guardStartTime = System.currentTimeMillis()
                        startGuardMonitor()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (gameView.playerState == PlayerState.GUARD) {
                        gameView.playerState = PlayerState.IDLE
                        guardStartTime = 0
                    }
                }
            }
            true
        }
        
        btnRetry.setOnClickListener {
            if (txtResult.text == getString(R.string.ending_message)) {
                recreate() 
            } else if (gameView.playerLife <= 0) {
                restartCurrentStage()
            } else {
                resultLayout.visibility = View.GONE
                showSelectionScreen()
            }
        }
    }

    private fun startGuardMonitor() {
        val monitorTask = object : Runnable {
            override fun run() {
                if (gameView.playerState == PlayerState.GUARD && guardStartTime > 0) {
                    val duration = System.currentTimeMillis() - guardStartTime
                    if (duration >= guardBreakLimit) {
                        // ガードブレイク（投げ飛ばし）
                        gameView.onGuardBreak()
                        guardStartTime = 0
                    } else {
                        handler.postDelayed(this, 100)
                    }
                }
            }
        }
        handler.post(monitorTask)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupController() {
        val onTouch = View.OnTouchListener { v, event ->
            val isPressed = event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE
            val isReleased = event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL
            
            when (v.id) {
                R.id.btnLeft -> { gameView.inputX = if (isReleased) 0f else -1f; if (event.action == MotionEvent.ACTION_DOWN) recordInput("L") }
                R.id.btnRight -> { gameView.inputX = if (isReleased) 0f else 1f; if (event.action == MotionEvent.ACTION_DOWN) recordInput("R") }
                R.id.btnUp -> { 
                    gameView.inputY = if (isReleased) 0f else -1f
                    if (event.action == MotionEvent.ACTION_DOWN) { gameView.startJump(); recordInput("U") }
                }
                R.id.btnDown -> { 
                    gameView.inputY = if (isReleased) 0f else 1f
                    if (event.action == MotionEvent.ACTION_DOWN) recordInput("D")
                }
            }
            true
        }
        findViewById<Button>(R.id.btnLeft).setOnTouchListener(onTouch)
        findViewById<Button>(R.id.btnRight).setOnTouchListener(onTouch)
        findViewById<Button>(R.id.btnUp).setOnTouchListener(onTouch)
        findViewById<Button>(R.id.btnDown).setOnTouchListener(onTouch)
    }

    private fun recordInput(key: String) {
        val now = System.currentTimeMillis()
        if (now - commandTimer > 500) lastInput = ""
        lastInput += key
        commandTimer = now
    }

    private fun showSelectionScreen() {
        selectLayout.visibility = View.VISIBLE
        gameView.visibility = View.GONE
        uiContainer.visibility = View.GONE
        controllerLayout.visibility = View.GONE
        stopBGM()

        updateSelectButton(btnDragon, EnemyType.DRAGON, R.string.enemy_dragon)
        updateSelectButton(btnGolem, EnemyType.GOLEM, R.string.enemy_golem)
        updateSelectButton(btnBeholder, EnemyType.BEHOLDER, R.string.enemy_beholder)

        if (defeatedEnemies.size >= 3) {
            btnDemonKing.visibility = View.VISIBLE
            updateSelectButton(btnDemonKing, EnemyType.DEMON_KING, R.string.enemy_demon_king)
        }
    }

    private fun updateSelectButton(btn: Button, type: EnemyType, nameRes: Int) {
        val isDefeated = defeatedEnemies.contains(type)
        btn.text = if (isDefeated) getString(nameRes) + " (" + getString(R.string.defeated) + ")" else getString(nameRes)
        btn.isEnabled = !isDefeated
        btn.alpha = if (isDefeated) 0.5f else 1.0f
    }

    private fun startStage(type: EnemyType) {
        selectLayout.visibility = View.GONE
        gameView.visibility = View.VISIBLE
        uiContainer.visibility = View.VISIBLE
        controllerLayout.visibility = View.VISIBLE

        val nameRes = when(type) {
            EnemyType.DRAGON -> R.string.enemy_dragon
            EnemyType.GOLEM -> R.string.enemy_golem
            EnemyType.BEHOLDER -> R.string.enemy_beholder
            EnemyType.DEMON_KING -> R.string.enemy_demon_king
        }
        txtEnemyName.text = getString(nameRes)
        
        gameView.reset(type)
        enemyHP.progress = 100
        updateLifeUI()
        startEnemyAI()
        playBGM(type == EnemyType.DEMON_KING)
    }

    private fun startEnemyAI() {
        enemyTimer?.cancel()
        enemyTimer = Timer()
        enemyTimer?.schedule(object : TimerTask() {
            override fun run() {
                handler.post {
                    if (gameView.isGameOver) checkGameOver()
                    else { gameView.spawnEnemyAttack(); updateLifeUI() }
                }
            }
        }, 2000, 1800)
    }

    private fun updateLifeUI() {
        val hearts = "❤".repeat(gameView.playerLife.coerceAtLeast(0))
        txtLife.text = getString(R.string.life_label, hearts)
    }

    private fun performAttack(isHeavy: Boolean) {
        if (gameView.isGameOver || gameView.playerState == PlayerState.RECOVERY || 
            gameView.playerState == PlayerState.THROWN || gameView.isBossEntering || gameView.isReadyGo) return

        if (gameView.canHadoken && lastInput.endsWith("DR")) {
            gameView.fireHadoken()
            applyDamage(15)
            gameView.showDamage(15)
            lastInput = ""
            return
        }

        if (gameView.canDiveAttack && gameView.playerState == PlayerState.JUMPING && gameView.inputY > 0) {
            gameView.playerState = PlayerState.DIVE_ATTACK
            val diveTask = object : Runnable {
                override fun run() {
                    if (gameView.playerState == PlayerState.DIVE_ATTACK) {
                        if (gameView.checkHit(PlayerState.DIVE_ATTACK)) {
                            applyDamage(45)
                            gameView.showDamage(45)
                        }
                        handler.postDelayed(this, 100)
                    }
                }
            }
            handler.post(diveTask)
            return
        }

        if (gameView.playerState != PlayerState.IDLE) return
        val damage = if (isHeavy) 25 else 10
        gameView.playerState = if (isHeavy) PlayerState.HEAVY_ATTACK else PlayerState.LIGHT_ATTACK
        handler.postDelayed({
            if (gameView.checkHit(gameView.playerState)) {
                applyDamage(damage)
                gameView.showDamage(damage)
            }
        }, if (isHeavy) 300L else 100L)
        handler.postDelayed({ if (!gameView.isGameOver && gameView.playerState != PlayerState.THROWN) gameView.playerState = PlayerState.IDLE }, if (isHeavy) 800L else 400L)
    }

    private fun applyDamage(amount: Int) {
        enemyHP.progress -= amount
        if (enemyHP.progress <= 0) {
            enemyHP.progress = 0
            gameView.isGameOver = true
            gameView.onEnemyDefeated()
            onVictory()
        }
    }

    private fun onVictory() {
        enemyTimer?.cancel()
        defeatedEnemies.add(gameView.currentEnemy)
        val isFinal = gameView.currentEnemy == EnemyType.DEMON_KING
        
        handler.postDelayed({
            if (isFinal) {
                txtResult.text = getString(R.string.ending_message)
                resultLayout.visibility = View.VISIBLE
            } else {
                showSkillSelect()
            }
            stopBGM()
        }, 3000)
    }

    private fun showSkillSelect() {
        skillSelectLayout.visibility = View.VISIBLE
        findViewById<Button>(R.id.btnSkillDoubleJump).isEnabled = !gameView.canDoubleJump
        findViewById<Button>(R.id.btnSkillDoubleJump).alpha = if (gameView.canDoubleJump) 0.5f else 1.0f
        findViewById<Button>(R.id.btnSkillDiveAttack).isEnabled = !gameView.canDiveAttack
        findViewById<Button>(R.id.btnSkillDiveAttack).alpha = if (gameView.canDiveAttack) 0.5f else 1.0f
        findViewById<Button>(R.id.btnSkillHadoken).isEnabled = !gameView.canHadoken
        findViewById<Button>(R.id.btnSkillHadoken).alpha = if (gameView.canHadoken) 0.5f else 1.0f
    }

    private fun learnSkill(type: Int) {
        when(type) {
            1 -> gameView.canDoubleJump = true
            2 -> gameView.canDiveAttack = true
            3 -> gameView.canHadoken = true
        }
        skillSelectLayout.visibility = View.GONE
        showSelectionScreen()
    }

    private fun checkGameOver() {
        if (gameView.playerLife <= 0) {
            enemyTimer?.cancel()
            txtResult.text = getString(R.string.game_over)
            btnRetry.text = getString(R.string.retry)
            resultLayout.visibility = View.VISIBLE
            stopBGM()
        }
    }

    private fun restartCurrentStage() {
        resultLayout.visibility = View.GONE
        startStage(gameView.currentEnemy)
    }

    private fun playBGM(isBoss: Boolean) {
        try {
            stopBGM()
            val uri = if (isBoss) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer()
            mediaPlayer?.setDataSource(this, uri)
            mediaPlayer?.isLooping = true
            mediaPlayer?.prepare()
            mediaPlayer?.start()
        } catch (e: Exception) {}
    }

    private fun stopBGM() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun playSeHit() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            sePlayer?.release()
            sePlayer = MediaPlayer.create(this, uri)
            sePlayer?.start()
        } catch (e: Exception) {}
    }

    fun playSeDamage() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            sePlayer?.release()
            sePlayer = MediaPlayer.create(this, uri)
            sePlayer?.setVolume(1.0f, 1.0f)
            sePlayer?.start()
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBGM()
        sePlayer?.release()
    }
}