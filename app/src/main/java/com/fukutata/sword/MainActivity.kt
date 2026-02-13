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
    private lateinit var playerLifeBar: ProgressBar
    private lateinit var txtResult: TextView
    private lateinit var txtEnemyName: TextView
    private lateinit var resultLayout: View
    private lateinit var titleLayout: View
    private lateinit var selectLayout: View
    private lateinit var skillSelectLayout: View
    private lateinit var uiContainer: View
    private lateinit var controllerLayout: View
    private lateinit var btnRetry: Button
    private lateinit var joystickView: JoystickView

    private lateinit var btnDragon: Button
    private lateinit var btnGolem: Button
    private lateinit var btnBeholder: Button
    private lateinit var btnGryphon: Button
    private lateinit var btnDemonKing: Button

    private val handler = Handler(Looper.getMainLooper())
    private var enemyTimer: Timer? = null
    private val defeatedEnemies = mutableSetOf<EnemyType>()

    private val bgm = BgmSynthesizer()
    private var sePlayer: MediaPlayer? = null

    private var lastInput = ""
    private var commandTimer: Long = 0
    
    private var guardStartTime: Long = 0
    private val guardBreakLimit = 3000L // 3秒

    // コンボ管理用：「タ(弱)・タ(弱)・ターン(強)」のリズム
    private var comboStep = 0
    private var lastAttackTime = 0L
    private val comboIntervalMax = 800L
    private val finisherIntervalMin = 100L
    private val finisherIntervalMax = 1500L

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gameView = findViewById(R.id.gameView)
        enemyHP = findViewById(R.id.enemyHP)
        playerLifeBar = findViewById(R.id.playerLifeBar)
        txtResult = findViewById(R.id.txtResult)
        txtEnemyName = findViewById(R.id.txtEnemyName)
        resultLayout = findViewById(R.id.resultLayout)
        titleLayout = findViewById(R.id.titleLayout)
        selectLayout = findViewById(R.id.selectLayout)
        skillSelectLayout = findViewById(R.id.skillSelectLayout)
        uiContainer = findViewById(R.id.uiContainer)
        controllerLayout = findViewById(R.id.controllerLayout)
        btnRetry = findViewById(R.id.btnRetry)
        joystickView = findViewById(R.id.joystickView)

        btnDragon = findViewById(R.id.btnSelectDragon)
        btnGolem = findViewById(R.id.btnSelectGolem)
        btnBeholder = findViewById(R.id.btnSelectBeholder)
        btnGryphon = findViewById(R.id.btnSelectGryphon)
        btnDemonKing = findViewById(R.id.btnSelectDemonKing)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            titleLayout.visibility = View.GONE
            showSelectionScreen()
        }

        btnDragon.setOnClickListener { startStage(EnemyType.DRAGON) }
        btnGolem.setOnClickListener { startStage(EnemyType.GOLEM) }
        btnBeholder.setOnClickListener { startStage(EnemyType.BEHOLDER) }
        btnGryphon.setOnClickListener { startStage(EnemyType.GRYPHON) }
        btnDemonKing.setOnClickListener { startStage(EnemyType.DEMON_KING) }

        setupJoystick()

        findViewById<Button>(R.id.btnSkillDoubleJump).setOnClickListener { learnSkill(1) }
        findViewById<Button>(R.id.btnSkillDiveAttack).setOnClickListener { learnSkill(2) }
        findViewById<Button>(R.id.btnSkillHadoken).setOnClickListener { learnSkill(3) }

        findViewById<Button>(R.id.btnLight).setOnClickListener { performAttack(false) }
        findViewById<Button>(R.id.btnHeavy).setOnClickListener { performAttack(true) }
        
        btnRetry.setOnClickListener {
            val currentResult = txtResult.text.toString()
            if (currentResult.contains("THANK YOU") || currentResult.contains("平和になった")) {
                recreate()
            } else if (currentResult.contains("勝利") || currentResult.contains("WIN") || currentResult.contains("習得") || currentResult.contains("次へ") || currentResult.contains("NEXT")) {
                resultLayout.visibility = View.GONE
                showSelectionScreen()
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

    private fun setupJoystick() {
        joystickView.onMoveListener = { x, y ->
            gameView.inputX = x
            gameView.inputY = y
            
            // 下入力判定
            gameView.isDownPressed = y > 0.4f
            
            // オートガード・しゃがみ状態制御
            if (!gameView.isGameOver && gameView.playerState != PlayerState.JUMPING && 
                gameView.playerState != PlayerState.LIGHT_ATTACK && gameView.playerState != PlayerState.HEAVY_ATTACK &&
                gameView.playerState != PlayerState.THROWN) {
                
                if (y > 0.6f) {
                    gameView.playerState = PlayerState.CROUCH
                } else if ((x < -0.5f && gameView.playerX < gameView.enemyX) || (x > 0.5f && gameView.playerX > gameView.enemyX)) {
                    if (gameView.playerState != PlayerState.GUARD) {
                        gameView.playerState = PlayerState.GUARD
                        guardStartTime = System.currentTimeMillis()
                        startGuardMonitor()
                    }
                } else {
                    gameView.playerState = PlayerState.IDLE
                    guardStartTime = 0
                }
            }

            if (y < -0.4f) gameView.startJump()
            if (Math.abs(x) > 0.5f || Math.abs(y) > 0.5f) {
                val dir = if (y < -0.4f) "U" else if (y > 0.4f) "D" else if (x < -0.5f) "L" else "R"
                recordInput(dir)
            }
        }
    }

    private fun recordInput(key: String) {
        val now = System.currentTimeMillis()
        if (now - commandTimer > 500) lastInput = ""
        if (lastInput.isEmpty() || lastInput.last().toString() != key) {
            lastInput += key
            commandTimer = now
        }
    }

    private fun showSelectionScreen() {
        selectLayout.visibility = View.VISIBLE
        gameView.visibility = View.GONE
        uiContainer.visibility = View.GONE
        controllerLayout.visibility = View.GONE
        bgm.stop()

        updateSelectButton(btnDragon, EnemyType.DRAGON, R.string.enemy_dragon)
        updateSelectButton(btnGolem, EnemyType.GOLEM, R.string.enemy_golem)
        updateSelectButton(btnBeholder, EnemyType.BEHOLDER, R.string.enemy_beholder)
        updateSelectButton(btnGryphon, EnemyType.GRYPHON, R.string.enemy_gryphon)

        if (defeatedEnemies.size >= 4) {
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
            EnemyType.GRYPHON -> R.string.enemy_gryphon
            EnemyType.DEMON_KING -> R.string.enemy_demon_king
        }
        txtEnemyName.text = getString(nameRes)
        
        gameView.reset(type)
        enemyHP.progress = 400
        updateLifeUI()
        startEnemyAI()
        bgm.start(type == EnemyType.DEMON_KING)
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
        playerLifeBar.progress = gameView.playerLife.toInt()
    }

    private fun performAttack(isHeavy: Boolean) {
        if (gameView.isGameOver || gameView.playerState == PlayerState.RECOVERY || 
            gameView.playerState == PlayerState.THROWN || gameView.isBossEntering || gameView.isReadyGo) return

        val now = System.currentTimeMillis()
        val interval = now - lastAttackTime
        
        if (gameView.canHadoken && lastInput.endsWith("DR")) {
            gameView.fireHadoken()
            applyDamage(25)
            gameView.showDamage(25)
            lastInput = ""
            return
        }

        if (gameView.canDiveAttack && gameView.playerState == PlayerState.JUMPING && gameView.isDownPressed) {
            gameView.playerState = PlayerState.DIVE_ATTACK
            val diveTask = object : Runnable {
                override fun run() {
                    if (gameView.playerState == PlayerState.DIVE_ATTACK) {
                        if (gameView.checkHit(PlayerState.DIVE_ATTACK)) {
                            applyDamage(60)
                            gameView.showDamage(60)
                        }
                        handler.postDelayed(this, 100)
                    }
                }
            }
            handler.post(diveTask)
            return
        }

        if (gameView.playerState != PlayerState.IDLE && gameView.playerState != PlayerState.CROUCH && gameView.playerState != PlayerState.GUARD) return

        var damage = if (isHeavy) 30 else 15
        var isFinisher = false

        if (!isHeavy) {
            if (comboStep == 0) {
                comboStep = 1
            } else if (comboStep == 1 && interval < comboIntervalMax) {
                comboStep = 2 
            } else {
                comboStep = 1
            }
        } else {
            if (comboStep == 2 && interval in finisherIntervalMin..finisherIntervalMax) {
                damage = 120 
                isFinisher = true
            }
            comboStep = 0
        }
        
        lastAttackTime = now

        gameView.playerState = if (isHeavy) PlayerState.HEAVY_ATTACK else PlayerState.LIGHT_ATTACK
        handler.postDelayed({
            if (gameView.checkHit(gameView.playerState)) {
                applyDamage(damage)
                gameView.showDamage(damage)
                if (isFinisher) {
                    gameView.showComboEffect() 
                }
            }
        }, if (isHeavy) 250L else 100L)
        
        handler.postDelayed({ 
            if (!gameView.isGameOver && gameView.playerState != PlayerState.THROWN) gameView.playerState = PlayerState.IDLE 
        }, if (isHeavy) 700L else 350L)
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
            controllerLayout.visibility = View.GONE
            val learnedCount = (if(gameView.canDoubleJump) 1 else 0) + (if(gameView.canDiveAttack) 1 else 0) + (if(gameView.canHadoken) 1 else 0)
            
            if (isFinal) {
                txtResult.text = getString(R.string.ending_message)
                btnRetry.text = getString(R.string.back_to_title)
                resultLayout.visibility = View.VISIBLE
            } else if (learnedCount < 3) {
                showSkillSelect()
            } else {
                // 技を全部覚えた後、または4匹目撃破時
                txtResult.text = getString(R.string.victory)
                btnRetry.text = getString(R.string.next_stage)
                resultLayout.visibility = View.VISIBLE
            }
            bgm.stop()
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
        val msg = when(type) {
            1 -> { gameView.canDoubleJump = true; "2段ジャンプ習得！\n【出し方：空中でもう一度 ↑】" }
            2 -> { gameView.canDiveAttack = true; "カブト斬り習得！\n【出し方：空中ジャンプ中に ↓ ＋ 攻撃】" }
            3 -> { gameView.canHadoken = true; "波動剣習得！\n【出し方：↓ → ＋ 攻撃】" }
            else -> ""
        }
        skillSelectLayout.visibility = View.GONE
        txtResult.text = msg
        btnRetry.text = getString(R.string.next_stage)
        resultLayout.visibility = View.VISIBLE
    }

    private fun checkGameOver() {
        if (gameView.playerLife <= 0) {
            enemyTimer?.cancel()
            gameView.gameResultMessage = "LOSE"
            controllerLayout.visibility = View.GONE
            txtResult.text = getString(R.string.game_over)
            btnRetry.text = getString(R.string.retry)
            resultLayout.visibility = View.VISIBLE
            bgm.stop()
        }
    }

    private fun restartCurrentStage() {
        resultLayout.visibility = View.GONE
        startStage(gameView.currentEnemy)
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
        bgm.stop()
        sePlayer?.release()
    }
}