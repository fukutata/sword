package com.fukutata.sword

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GameView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    // --- プロパティ ---
    var playerX = 100f
    var playerY = 500f
    var enemyX = 800f
    var enemyY = 500f
    var playerLife = 400
    var playerState = PlayerState.IDLE // PlayerState.ktが別にある前提
    var currentEnemy = EnemyType.DRAGON
    var isGameOver = false
    var isBossEntering = false
    var isReadyGo = false
    var gameResultMessage = ""

    // MainActivityからの入力用
    var inputX = 0f
    var inputY = 0f
    var isDownPressed = false

    // --- スキル習得フラグ ---
    var canDoubleJump = false
    var canDiveAttack = false
    var canHadoken = false

    private val paint = Paint().apply {
        isAntiAlias = true
        textSize = 50f
    }

    // --- MainActivityから呼ばれるメソッド群 ---
    fun reset(type: EnemyType) {
        currentEnemy = type
        playerLife = 400
        isGameOver = false
        playerState = PlayerState.IDLE
        invalidate()
    }

    fun startJump() {
        if (playerState != PlayerState.JUMPING) {
            playerState = PlayerState.JUMPING
            invalidate()
        }
    }

    fun fireHadoken() { invalidate() }
    fun onGuardBreak() { playerState = PlayerState.RECOVERY; invalidate() }
    fun checkHit(state: PlayerState): Boolean { return true }
    fun showDamage(amount: Int) {}
    fun showComboEffect() {}

    fun spawnEnemyAttack() {
        if (!isGameOver) {
            invalidate()
        }
    }

    fun onEnemyDefeated() {
        isGameOver = true
        gameResultMessage = "WIN"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)

        paint.color = Color.BLUE
        canvas.drawRect(playerX, playerY, playerX + 100f, playerY + 100f, paint)

        paint.color = Color.RED
        canvas.drawRect(enemyX, enemyY, enemyX + 100f, enemyY + 100f, paint)

        paint.color = Color.WHITE
        canvas.drawText("Enemy: ${currentEnemy.name}", 50f, 80f, paint)
        canvas.drawText("Player HP: $playerLife", 50f, 150f, paint)

        if (isGameOver) {
            paint.textSize = 100f
            paint.color = Color.YELLOW
            canvas.drawText(gameResultMessage, width / 2f - 100f, height / 2f, paint)
        }
    }
}

/**
 * EnemyType がプロジェクト内のどこにも定義されていないため、
 * ここで定義します。
 */
enum class EnemyType {
    DRAGON, GOLEM, BEHOLDER, GRYPHON, DEMON_KING
}