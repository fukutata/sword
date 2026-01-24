package com.fukutata.sword

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

enum class EnemyType {
    DRAGON, GOLEM, BEHOLDER, DEMON_KING
}

data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var color: Int, var life: Int)

class GameView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val paint = Paint().apply { isAntiAlias = true }
    private val textPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 120f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    // ゲーム状態
    var playerState = PlayerState.IDLE
    var playerX = 200f
    var playerY = 0f
    var playerLife = 5
    var isGameOver = false
    var inputX = 0f
    var inputY = 0f
    var isDownPressed = false
    
    var jumpCount = 0
    var canDoubleJump = false
    var canDiveAttack = false
    var canHadoken = false

    var currentEnemy = EnemyType.DRAGON
    val enemyRect = RectF(0f, 0f, 0f, 0f)
    var enemyX = 0f
    var enemyY = 0f
    var enemyFlash = 0
    private var frameCount = 0

    // 演出状態
    var isReadyGo = false
    var readyGoTimer = 0
    var isBossEntering = false
    var bossEntryY = -500f
    private val particles = mutableListOf<Particle>()
    var isSlowMotion = false
    private var slowMotionTimer = 0

    // 攻撃物
    var projectileX = -500f
    var projectileY = 0f
    var projectileType = 0 
    var laserAlpha = 0
    private var laserHasHit = false
    var hadokenX = -500f
    var hadokenY = 0f

    private var enemyActionTimer = 0
    private var isBeholderInvisible = false

    private var damageText = ""
    private var damageTimer = 0
    var screenShake = 0f
    private var groundY = 0f
    private var velocityY = 0f
    
    private val gravity = 1.6f
    private val jumpPower = -38f
    private val moveSpeed = 16f
    private var enemyMoveSpeed = 2.5f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        groundY = h * 0.7f
        playerY = groundY
        setupEnemyPos(w, h)
    }

    private fun setupEnemyPos(w: Int, h: Int) {
        val size = h * 0.45f
        enemyX = w * 0.7f
        enemyY = groundY + 160f - size
        enemyRect.set(enemyX, enemyY, enemyX + size, enemyY + size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        frameCount++

        if (screenShake > 0) {
            canvas.translate((Math.random().toFloat() - 0.5f) * screenShake, (Math.random().toFloat() - 0.5f) * screenShake)
            screenShake *= 0.85f
        }

        drawDungeon(canvas)
        
        val updateCount = if (isSlowMotion) {
            slowMotionTimer--
            if (slowMotionTimer <= 0) isSlowMotion = false
            if (frameCount % 3 == 0) 1 else 0
        } else {
            1
        }

        if (updateCount > 0) {
            if (isBossEntering) {
                updateBossEntry()
            } else if (isReadyGo) {
                updateReadyGo()
            } else if (!isGameOver) {
                updatePhysics()
                updateEnemyAI()
            }
            updateParticles()
        }

        drawEnemy(canvas)
        drawProjectiles(canvas)
        drawPlayer(canvas)
        drawParticles(canvas)

        if (isReadyGo) {
            val text = if (readyGoTimer > 60) "READY" else "GO!"
            textPaint.color = if (readyGoTimer > 60) Color.YELLOW else Color.RED
            canvas.drawText(text, width / 2f, height / 2f, textPaint)
        }

        if (damageTimer > 0) {
            textPaint.color = Color.YELLOW
            textPaint.textSize = 60f
            textPaint.alpha = (damageTimer * 8).coerceAtMost(255)
            canvas.drawText(damageText, enemyRect.centerX(), enemyRect.top - 50, textPaint)
            damageTimer--
            textPaint.textSize = 120f
        }
        invalidate()
    }

    private fun updateBossEntry() {
        if (bossEntryY < enemyY) bossEntryY += 4f
        else { isBossEntering = false; startReadyGo() }
    }

    private fun updateReadyGo() {
        readyGoTimer--
        if (readyGoTimer <= 0) isReadyGo = false
    }

    private fun updateEnemyAI() {
        if (isGameOver || isReadyGo || isBossEntering) return
        val targetX = if (isBeholderInvisible) enemyX else playerX + (if (playerX < enemyX) 350f else -350f)
        if (enemyX > targetX + 10) enemyX -= enemyMoveSpeed
        else if (enemyX < targetX - 10) enemyX += enemyMoveSpeed
        enemyX = enemyX.coerceIn(width * 0.1f, width - enemyRect.width())
        val hover = if (currentEnemy != EnemyType.GOLEM) sin(frameCount * 0.08).toFloat() * 25f else 0f
        enemyRect.offsetTo(enemyX, enemyY + hover)
        enemyActionTimer++
        if (enemyActionTimer > 150) { performEnemySpecialAction(); enemyActionTimer = 0 }
    }

    private fun performEnemySpecialAction() {
        when (currentEnemy) {
            EnemyType.DRAGON -> {
                if (Math.abs(enemyX - playerX) < 500f) { projectileType = 3; projectileX = enemyX; projectileY = enemyY + 200f }
            }
            EnemyType.GOLEM -> {
                if (Math.abs(enemyX - playerX) < 300f && playerState != PlayerState.JUMPING) { projectileType = 4; projectileX = enemyX; projectileY = enemyY + 150f }
            }
            EnemyType.BEHOLDER -> {
                isBeholderInvisible = true
                spawnExplosion(enemyRect.centerX(), enemyRect.centerY(), Color.MAGENTA)
                postDelayed({
                    enemyX = if (enemyX > width / 2) width * 0.2f else width * 0.8f
                    isBeholderInvisible = false
                    spawnExplosion(enemyRect.centerX(), enemyRect.centerY(), Color.MAGENTA)
                }, 800)
            }
            else -> {}
        }
    }

    private fun updateParticles() {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx; p.y += p.vy; p.vy += 0.2f; p.life--
            if (p.life <= 0) iterator.remove()
        }
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            paint.color = p.color; paint.alpha = (p.life * 5).coerceIn(0, 255)
            canvas.drawRect(p.x, p.y, p.x + 10, p.y + 10, paint)
        }
        paint.alpha = 255
    }

    fun spawnExplosion(x: Float, y: Float, color: Int) {
        for (i in 0..40) {
            val vx = (Math.random().toFloat() - 0.5f) * 15f
            val vy = (Math.random().toFloat() - 0.8f) * 15f
            particles.add(Particle(x, y, vx, vy, color, 80))
        }
    }

    fun startVictorySlow() { isSlowMotion = true; slowMotionTimer = 120 }

    private fun drawDungeon(canvas: Canvas) {
        canvas.drawColor(if (currentEnemy == EnemyType.DEMON_KING) Color.parseColor("#220000") else Color.parseColor("#121212"))
        paint.color = Color.parseColor("#1F1F1F")
        for (i in 0..15) {
            val offset = (frameCount % 100) * 2f
            canvas.drawRect((i * 450f) - offset, 0f, (i * 450f + 100f) - offset, height.toFloat(), paint)
        }
        paint.color = Color.parseColor("#0A0A0A")
        canvas.drawRect(0f, groundY + 160f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawEnemy(canvas: Canvas) {
        if (isGameOver && enemyHP_internal <= 0) return 
        if (isBeholderInvisible) return
        val rect = if (currentEnemy == EnemyType.DEMON_KING && isBossEntering) {
            RectF(enemyRect.left, bossEntryY, enemyRect.right, bossEntryY + enemyRect.height())
        } else { enemyRect }
        if (enemyFlash > 0) { paint.colorFilter = LightingColorFilter(0xFFFFFF, 0xFFFFFF); enemyFlash-- } else { paint.colorFilter = null }
        when (currentEnemy) {
            EnemyType.DRAGON -> drawDragon(canvas, rect)
            EnemyType.GOLEM -> drawGolem(canvas, rect)
            EnemyType.BEHOLDER -> drawBeholder(canvas, rect)
            EnemyType.DEMON_KING -> drawDemonKing(canvas, rect)
        }
        paint.colorFilter = null
    }

    private var enemyHP_internal = 100 

    private fun drawDragon(canvas: Canvas, rect: RectF) {
        paint.color = Color.parseColor("#1B5E20"); canvas.drawOval(rect, paint)
        val headRect = if (playerX < enemyX) RectF(rect.left - 80, rect.top + 20, rect.left + 40, rect.top + 120) 
                       else RectF(rect.right - 40, rect.top + 20, rect.right + 80, rect.top + 120)
        canvas.drawOval(headRect, paint)
        paint.color = Color.YELLOW; canvas.drawCircle(if(playerX < enemyX) headRect.left + 30 else headRect.right - 30, headRect.top + 50, 12f, paint)
    }

    private fun drawGolem(canvas: Canvas, rect: RectF) {
        paint.color = Color.parseColor("#4E342E")
        canvas.drawRect(rect.left + 40, rect.top + 60, rect.right - 40, rect.bottom, paint)
        canvas.drawRect(rect.left + 70, rect.top + 10, rect.right - 70, rect.top + 70, paint)
        paint.color = Color.RED; canvas.drawCircle(rect.centerX() - 25, rect.top + 40, 10f, paint)
        canvas.drawCircle(rect.centerX() + 25, rect.top + 40, 10f, paint)
    }

    private fun drawBeholder(canvas: Canvas, rect: RectF) {
        paint.color = Color.parseColor("#6A1B9A"); canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width()/2.2f, paint)
        paint.color = Color.WHITE; canvas.drawCircle(rect.centerX() + (if(playerX < enemyX) -40 else 40), rect.centerY(), 60f, paint)
        paint.color = Color.BLACK; canvas.drawCircle(rect.centerX() + (if(playerX < enemyX) -55 else 55), rect.centerY(), 15f, paint)
    }

    private fun drawDemonKing(canvas: Canvas, rect: RectF) {
        paint.color = Color.BLACK; canvas.drawOval(rect, paint)
        paint.color = Color.RED; canvas.drawCircle(rect.centerX() - 70, rect.top + 120, 25f, paint); canvas.drawCircle(rect.centerX() + 70, rect.top + 120, 25f, paint)
    }

    private fun drawProjectiles(canvas: Canvas) {
        if (isBossEntering || isReadyGo) return
        if (laserAlpha > 0) {
            paint.color = Color.WHITE; paint.alpha = laserAlpha
            canvas.drawRect(0f, enemyRect.centerY() - 25, width.toFloat(), enemyRect.centerY() + 25, paint)
            if (!laserHasHit && laserAlpha > 180) {
                if (getPlayerRect().top < enemyRect.centerY() + 65 && getPlayerRect().bottom > enemyRect.centerY() - 65) {
                    onPlayerHit(); laserHasHit = true
                }
            }
            laserAlpha -= 10
        }
        if (hadokenX != -500f) {
            hadokenX += if (playerX < enemyX) 30f else -30f
            paint.color = Color.CYAN; canvas.drawCircle(hadokenX, hadokenY, 40f, paint)
            if (RectF.intersects(RectF(hadokenX-50, hadokenY-50, hadokenX+50, hadokenY+50), enemyRect)) hadokenX = -500f
            if (hadokenX > width || hadokenX < 0) hadokenX = -500f
        }
    }

    private fun drawPlayer(canvas: Canvas) {
        val pRect = getPlayerRect()
        val isFacingRight = playerX < enemyX
        
        // Sword Visual (Legendary Sword)
        if (playerState == PlayerState.LIGHT_ATTACK || playerState == PlayerState.HEAVY_ATTACK || playerState == PlayerState.DIVE_ATTACK) {
            paint.color = if (playerState == PlayerState.HEAVY_ATTACK) Color.YELLOW else if (playerState == PlayerState.DIVE_ATTACK) Color.CYAN else Color.WHITE
            paint.alpha = 180
            val range = if (playerState == PlayerState.HEAVY_ATTACK) 450f else 280f
            val attackRect = if (isFacingRight) RectF(pRect.right, pRect.top - 20, pRect.right + range, pRect.bottom + 20)
                             else RectF(pRect.left - range, pRect.top - 20, pRect.left, pRect.bottom + 20)
            canvas.drawRect(attackRect, paint)
            paint.alpha = 255
        }
        if (playerState == PlayerState.GUARD) {
            paint.color = Color.GREEN; paint.alpha = 100; canvas.drawCircle(pRect.centerX(), pRect.centerY(), 120f, paint); paint.alpha = 255
        }
        drawHero(canvas, pRect, isFacingRight)
    }

    private fun drawHero(canvas: Canvas, rect: RectF, isFacingRight: Boolean) {
        canvas.save()
        if (playerState == PlayerState.THROWN) canvas.rotate(frameCount * 20f, rect.centerX(), rect.centerY())
        else if (playerState == PlayerState.DIVE_ATTACK) canvas.rotate(180f, rect.centerX(), rect.centerY())
        if (!isFacingRight) canvas.scale(-1f, 1f, rect.centerX(), rect.centerY())
        
        // Cape (Animated)
        paint.color = Color.parseColor("#D32F2F")
        val capePath = Path()
        val flutter = sin(frameCount * 0.2f) * 20f
        capePath.moveTo(rect.centerX(), rect.top + 40)
        capePath.lineTo(rect.left - 60, rect.centerY() + flutter)
        capePath.lineTo(rect.left - 20, rect.bottom + flutter / 2)
        capePath.close()
        canvas.drawPath(capePath, paint)

        // Armor (Detailed)
        paint.color = Color.parseColor("#1976D2")
        canvas.drawRoundRect(RectF(rect.left + 35, rect.top + 60, rect.right - 35, rect.bottom - 10), 15f, 15f, paint)
        paint.color = Color.parseColor("#BBDEFB"); paint.alpha = 100
        canvas.drawRect(rect.left + 45, rect.top + 70, rect.centerX(), rect.bottom - 30, paint) // Highlight
        paint.alpha = 255

        // Shoulder Guard
        paint.color = Color.parseColor("#1565C0")
        canvas.drawCircle(rect.left + 40, rect.top + 70, 25f, paint)
        canvas.drawCircle(rect.right - 40, rect.top + 70, 25f, paint)

        // Head & Helmet
        paint.color = Color.parseColor("#FFCCBC"); canvas.drawCircle(rect.centerX(), rect.top + 35, 35f, paint) // Face
        paint.color = Color.parseColor("#455A64") // Helmet
        val helmPath = Path()
        helmPath.moveTo(rect.centerX() - 40, rect.top + 30)
        helmPath.lineTo(rect.centerX() + 40, rect.top + 30)
        helmPath.lineTo(rect.centerX(), rect.top - 20)
        helmPath.close()
        canvas.drawPath(helmPath, paint)
        
        // Helmet Crest (Red feather)
        paint.color = Color.RED
        canvas.drawCircle(rect.centerX(), rect.top - 20, 12f, paint)

        // The Legendary Sword (Improved Visual)
        drawLegendarySword(canvas, rect)
        
        canvas.restore()
    }

    private fun drawLegendarySword(canvas: Canvas, rect: RectF) {
        paint.color = Color.parseColor("#BDBDBD") // Blade Silver
        val swordPath = Path()
        if (playerState == PlayerState.LIGHT_ATTACK || playerState == PlayerState.HEAVY_ATTACK) {
            swordPath.moveTo(rect.right - 10, rect.centerY())
            swordPath.lineTo(rect.right + 120, rect.centerY() - 15)
            swordPath.lineTo(rect.right + 140, rect.centerY()) // Pointy tip
            swordPath.lineTo(rect.right + 120, rect.centerY() + 15)
            swordPath.close()
            canvas.drawPath(swordPath, paint)
            
            // Hilt & Guard
            paint.color = Color.parseColor("#FFD700") // Gold guard
            canvas.drawRect(rect.right - 15, rect.centerY() - 40, rect.right + 5, rect.centerY() + 40, paint)
            paint.color = Color.parseColor("#5D4037") // Wood handle
            canvas.drawRect(rect.right - 35, rect.centerY() - 10, rect.right - 15, rect.centerY() + 10, paint)
        } else {
            // Idle/Jump position
            swordPath.moveTo(rect.left + 20, rect.centerY() - 20)
            swordPath.lineTo(rect.left - 30, rect.top - 60)
            swordPath.lineTo(rect.left - 45, rect.top - 75)
            swordPath.lineTo(rect.left - 15, rect.top - 50)
            swordPath.close()
            canvas.drawPath(swordPath, paint)
            
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(rect.left, rect.centerY() - 40, rect.left + 40, rect.centerY() - 30, paint)
        }
    }

    private fun updatePhysics() {
        if (playerState == PlayerState.THROWN) {
            playerX += if (playerX < enemyX) -30f else 30f; playerY -= 10f
            if (playerX < 0f || playerX > width - 160f) { playerState = PlayerState.IDLE; screenShake = 40f }
            return
        }
        if (inputX != 0f && playerState != PlayerState.DIVE_ATTACK && playerState != PlayerState.GUARD) {
            playerX += inputX * moveSpeed; playerX = playerX.coerceIn(0f, width - 160f)
        }
        if (playerY < groundY || velocityY < 0) {
            velocityY += gravity; playerY += velocityY
            if (playerY > groundY) {
                playerY = groundY; velocityY = 0f
                if (playerState == PlayerState.JUMPING || playerState == PlayerState.DIVE_ATTACK) {
                    if (playerState == PlayerState.DIVE_ATTACK) screenShake = 25f
                    playerState = PlayerState.IDLE; jumpCount = 0
                }
            }
        }
    }

    fun getPlayerRect() = RectF(playerX, playerY, playerX + 160f, playerY + 160f)

    fun checkHit(type: PlayerState): Boolean {
        if (isBossEntering || isReadyGo || isGameOver) return false
        val range = if (type == PlayerState.HEAVY_ATTACK) 450f else 280f
        val isFacingRight = playerX < enemyX
        val attackRect = if (isFacingRight) RectF(playerX + 160f, playerY - 50f, playerX + 160f + range, playerY + 210f)
                         else RectF(playerX - range, playerY - 50f, playerX, playerY + 210f)
        val isHit = RectF.intersects(attackRect, enemyRect)
        if (isHit) { screenShake = if (type == PlayerState.HEAVY_ATTACK) 35f else 12f; (context as? MainActivity)?.playSeHit() }
        return isHit
    }

    fun onGuardBreak() { if (playerState == PlayerState.GUARD) onPlayerGrabbed() }
    private fun onPlayerGrabbed() { playerState = PlayerState.THROWN; screenShake = 30f; playerLife--; (context as? MainActivity)?.playSeDamage() }
    fun showDamage(amount: Int) { damageText = amount.toString(); damageTimer = 40; enemyFlash = 5 }

    fun startJump() {
        if (isGameOver || playerState != PlayerState.IDLE && playerState != PlayerState.JUMPING) return
        if (playerY >= groundY) { velocityY = jumpPower; playerState = PlayerState.JUMPING; jumpCount = 1 }
        else if (canDoubleJump && jumpCount == 1) { velocityY = jumpPower * 0.85f; jumpCount = 2 }
    }

    fun spawnEnemyAttack() {
        if (isGameOver || isBossEntering || isReadyGo) return
        laserHasHit = false
        when (currentEnemy) {
            EnemyType.DRAGON -> { projectileX = enemyRect.left; projectileY = enemyRect.centerY() }
            EnemyType.GOLEM -> { projectileX = enemyRect.left; projectileY = enemyRect.centerY() + 80 }
            EnemyType.BEHOLDER -> { laserAlpha = 255 }
            EnemyType.DEMON_KING -> { laserAlpha = 255 }
        }
    }

    private fun onPlayerHit() {
        if (isGameOver || isBossEntering || isReadyGo) return
        if (playerState == PlayerState.GUARD) { screenShake = 5f; return }
        playerLife--; projectileX = -500f; screenShake = 30f
        (context as? MainActivity)?.playSeDamage()
        if (playerLife <= 0) { playerLife = 0; isGameOver = true }
    }

    fun fireHadoken() {
        if (!canHadoken || hadokenX != -500f || isBossEntering || isReadyGo) return
        hadokenX = playerX + (if(playerX < enemyX) 160f else -40f); hadokenY = playerY + 80f
    }

    fun onEnemyDefeated() { enemyHP_internal = 0; startVictorySlow() }
    fun startReadyGo() { isReadyGo = true; readyGoTimer = 100 }

    fun reset(enemy: EnemyType) {
        currentEnemy = enemy; playerX = 200f; playerY = groundY; playerLife = 5
        projectileX = -500f; hadokenX = -500f; laserAlpha = 0; laserHasHit = false
        velocityY = 0f; isGameOver = false; playerState = PlayerState.IDLE
        damageTimer = 0; inputX = 0f; inputY = 0f; jumpCount = 0; enemyHP_internal = 100
        particles.clear(); isSlowMotion = false; slowMotionTimer = 0; enemyActionTimer = 0; isBeholderInvisible = false
        setupEnemyPos(width, height)
        if (enemy == EnemyType.DEMON_KING) { bossEntryY = -600f; isBossEntering = true }
        else { isBossEntering = false; startReadyGo() }
    }
}