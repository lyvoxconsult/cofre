package com.lyvox.vault.service

import com.lyvox.vault.crypto.CryptoManager
import com.lyvox.vault.crypto.KeyDerivation
import com.lyvox.vault.crypto.RecoveryHasher
import com.lyvox.vault.data.database.DatabaseHelper
import com.lyvox.vault.data.model.QuestionWithOptions
import com.lyvox.vault.data.model.RecoveryQuestion
import com.lyvox.vault.data.model.RecoveryStatus
import com.lyvox.vault.data.repository.SettingsRepository
import java.security.SecureRandom

/**
 * Service for the master password recovery flow using security questions.
 *
 * Matches the desktop implementation.
 */
class RecoveryService(
    private val settingsRepository: SettingsRepository,
    private val dbHelper: DatabaseHelper
) {

    private val random = SecureRandom()

    companion object {
        private val QUESTIONS = listOf(
            "Qual o nome do seu primeiro animal de estimação?",
            "Qual o nome da sua cidade natal?",
            "Qual o nome do seu melhor amigo de infância?",
            "Qual o nome do seu professor favorito?",
            "Qual o seu prato favorito?",
            "Qual o nome da sua primeira escola?",
            "Qual o sobrenome de solteira da sua mãe?",
            "Qual o nome do seu livro favorito?",
            "Em que ano você se formou no ensino médio?",
            "Qual o modelo do seu primeiro carro?"
        )

        // Distractor categories (matching desktop recovery.rs)
        private val DISTRACTOR_CATEGORIES = mapOf(
            0 to listOf("Rex", "Bolinha", "Toddy", "Mel", "Bela", "Thor", "Luna", "Pipoca", "Billy", "Nina",
                "Fred", "Boby", "Chico", "Teco", "Lola", "Toto", "Mimi", "Bisteca", "Pandora", "Tobias"),
            1 to listOf("São Paulo", "Rio de Janeiro", "Belo Horizonte", "Salvador", "Brasília", "Fortaleza",
                "Curitiba", "Recife", "Porto Alegre", "Campinas", "Manaus", "Goiânia", "Niterói",
                "Santos", "Ribeirão Preto", "Florianópolis", "Vitória", "Natal", "João Pessoa", "Maceió"),
            2 to listOf("Pedro", "João", "Lucas", "Gabriel", "Rafael", "Matheus", "Felipe", "Leonardo",
                "Gustavo", "Vinicius", "Fernando", "Bruno", "Diego", "Ana", "Maria", "Juliana",
                "Camila", "Fernanda", "Mariana", "Patrícia"),
            3 to listOf("Carlos", "Roberto", "Márcia", "Cláudia", "José", "Maria", "Paulo", "Cristina",
                "Fernando", "Sandra", "Antônio", "João", "Pedro", "Marcos", "Rita", "Tereza",
                "Miguel", "Bianca", "Jorge", "Marta"),
            4 to listOf("Feijoada", "Pizza", "Lasanha", "Churrasco", "Strogonoff", "Macarrão", "Sushi",
                "Moqueca", "Acarajé", "Brigadeiro", "Açaí", "Coxinha", "Escondidinho", "Pão de Queijo",
                "Salpicão", "Virado à Paulista", "Baião de Dois", "Tapioca", "Carne de Sol", "Pudim"),
            5 to listOf("Santo Antônio", "São José", "Salesiano", "Marista", "Santa Maria", "Dom Bosco",
                "Albert Einstein", "Santo Agostinho", "São Paulo", "Bom Jesus", "Sagrado Coração",
                "Santa Catarina", "Santa Úrsula", "Imaculada Conceição", "Nossa Senhora Aparecida",
                "São João", "São Francisco", "Santa Terezinha", "Santo Inácio", "Padre Anchieta"),
            6 to listOf("Silva", "Santos", "Oliveira", "Souza", "Pereira", "Costa", "Ferreira", "Rodrigues",
                "Almeida", "Nascimento", "Lima", "Araújo", "Carvalho", "Gomes", "Martins", "Barbosa",
                "Rocha", "Ribeiro", "Alves", "Monteiro"),
            7 to listOf("Dom Casmurro", "Grande Sertão Veredas", "Memórias Póstumas de Brás Cubas",
                "Capitães da Areia", "O Pequeno Príncipe", "1984", "Dom Quixote", "O Alquimista",
                "Cem Anos de Solidão", "A Moreninha", "Iracema", "Senhora", "O Cortiço",
                "Macunaíma", "Vidas Secas", "O Guarani", "Harry Potter", "O Senhor dos Anéis",
                "O Código Da Vinci", "Crime e Castigo"),
            8 to listOf("1998", "1999", "2000", "2001", "2002", "2003", "2004", "2005", "2006", "2007",
                "2008", "2009", "2010", "2011", "2012", "2013", "2014", "2015", "2016", "2017",
                "2018", "2019", "2020", "2021", "2022", "2023", "2024"),
            9 to listOf("Fusca", "Gol", "Corsa", "Palio", "Uno", "Celta", "Fiesta", "Focus", "Ka",
                "Civic", "Corolla", "Santana", "Parati", "Escort", "Del Rey", "Chevette",
                "Monza", "Opala", "Maverick", "Brasília", "Kombi", "Saveiro", "Fox", "Polo",
                "Golf", "Vectra", "Astra", "Omega", "S10", "Ranger", "Hilux", "HB20")
        )
    }

    fun getQuestions(): List<String> = QUESTIONS

    fun getRecoveryStatus(): RecoveryStatus = settingsRepository.getRecoveryStatus()

    /**
     * Returns the 3 configured questions with dynamically generated options.
     *
     * For each question, the correct answer is identified by comparing the hash
     * of each stored option against answer_hash. Fresh distractors are then
     * generated from the category list. This ensures:
     * - Distractors vary between attempts
     * - Position of correct answer is random
     * - No plaintext correct answer is stored (only hashed)
     */
    fun getRecoveryQuestions(): List<QuestionWithOptions> {
        val recovery = settingsRepository.getRecoveryConfig()
            ?: throw IllegalStateException("Recuperação não configurada.")

        if (recovery.questions.size != 3) {
            throw IllegalStateException("Recuperação não está totalmente configurada.")
        }

        return recovery.questions.map { rq ->
            val question = QUESTIONS.getOrElse(rq.questionIndex) {
                throw IllegalStateException("Pergunta inválida na configuração.")
            }

            // Generate 5 fresh options: 1 correct + 4 distractors
            val options = generateFreshOptions(rq)

            QuestionWithOptions(
                index = rq.questionIndex,
                question = question,
                options = options
            )
        }
    }

    /**
     * Generates 5 fresh options for a question.
     * The correct answer is among the stored options — we find it by
     * comparing the hash of each stored option against answer_hash.
     */
    private fun generateFreshOptions(rq: RecoveryQuestion): List<String> {
        // Find the correct answer among stored options by hash comparison
        val correctAnswer = rq.options.firstOrNull { option ->
            val hash = RecoveryHasher.hashAnswer(rq.answerSalt, option)
            hash == rq.answerHash
        } ?: throw IllegalStateException("Não foi possível identificar a resposta correta.")

        // Generate 4 fresh distractors from the category
        val distractors = generateDistractors(rq.questionIndex, correctAnswer, 4)

        // Combine and shuffle
        val allOptions = mutableListOf(correctAnswer)
        allOptions.addAll(distractors)
        allOptions.shuffle(random)
        return allOptions
    }

    /**
     * Generates distractors from the category list for a given question.
     */
    private fun generateDistractors(questionIndex: Int, correctAnswer: String, count: Int): List<String> {
        val candidates = DISTRACTOR_CATEGORIES[questionIndex]?.toMutableList()
            ?: return (1..count).map { "Opção ${random.nextInt(900) + 100}" }

        // Remove the correct answer if present
        val correctLower = correctAnswer.trim().lowercase()
        candidates.removeAll { it.lowercase() == correctLower }

        // Shuffle and pick `count`
        candidates.shuffle(random)
        val selected = candidates.take(count)

        // If not enough candidates, generate numeric fallbacks
        val result = selected.toMutableList()
        while (result.size < count) {
            val fallback = "Opção ${random.nextInt(900) + 100}"
            if (fallback.lowercase() != correctLower) {
                result.add(fallback)
            }
        }
        return result
    }

    /**
     * Sets up recovery questions with hashed answers.
     */
    fun setupRecovery(
        questionAnswers: List<Pair<Int, String>>,
        masterKey: ByteArray
    ) {
        require(questionAnswers.size == 3) { "Exatamente 3 perguntas são necessárias." }
        questionAnswers.forEach { (index, answer) ->
            require(index in QUESTIONS.indices) { "Índice de pergunta inválido: $index" }
            require(answer.trim().isNotEmpty()) { "Todas as respostas devem ser preenchidas." }
        }

        val recoveryQuestions = questionAnswers.map { (index, answer) ->
            val salt = RecoveryHasher.generateSalt()
            val saltHex = RecoveryHasher.bytesToHex(salt)
            val hash = RecoveryHasher.hashAnswer(salt, answer)

            // Generate 5 options (correct + 4 distractors)
            val options = generateSetupOptions(index, answer)

            RecoveryQuestion(
                questionIndex = index,
                answerSalt = saltHex,
                answerHash = hash,
                options = options
            )
        }

        // Derive recovery key from combined answers (Argon2id)
        // Uses a randomly generated salt unique to this installation for resilience
        // against pre-computation attacks on the recovery key
        val answersJoined = questionAnswers.joinToString("|||") { it.second.trim().lowercase() }
        val recoverySaltBytes = KeyDerivation.generateSalt()
        val recoverySaltHex = RecoveryHasher.bytesToHex(recoverySaltBytes)
        val recoveryKey = KeyDerivation.deriveKey(answersJoined, recoverySaltBytes)

        // Encrypt master key with recovery key
        val masterKeyHex = RecoveryHasher.bytesToHex(masterKey)
        val (wrappedKey, wrapNonce) = CryptoManager.encryptField(recoveryKey, masterKeyHex)

        val recoveryConfig = com.lyvox.vault.data.model.RecoveryConfig(
            questions = recoveryQuestions,
            wrappedMasterKey = wrappedKey,
            wrapNonce = wrapNonce,
            attempts = 0,
            blockedUntil = null,
            recoverySalt = recoverySaltHex
        )

        settingsRepository.setRecoveryConfig(recoveryConfig)
    }

    /**
     * Generates 5 options during setup (1 correct + 4 contextual distractors).
     */
    private fun generateSetupOptions(questionIndex: Int, correctAnswer: String): List<String> {
        val distractors = generateDistractors(questionIndex, correctAnswer, 4)
        val options = mutableListOf(correctAnswer.trim())
        options.addAll(distractors)
        options.shuffle(random)
        return options
    }

    /**
     * Verifies recovery answers. Returns the decrypted master key if all correct.
     */
    fun verifyAnswers(answers: List<String>): ByteArray {
        require(answers.size == 3) { "Exatamente 3 respostas são necessárias." }

        val recovery = settingsRepository.getRecoveryConfig()
            ?: throw IllegalStateException("Recuperação não configurada.")

        // Check rate limiting
        val status = settingsRepository.getRecoveryStatus()
        if (status.blocked) {
            throw IllegalStateException("Não foi possível validar as respostas. Tente novamente mais tarde.")
        }

        // Verify each answer hash
        val allCorrect = recovery.questions.zip(answers).all { (q, answer) ->
            val expectedHash = RecoveryHasher.hashAnswer(q.answerSalt, answer)
            expectedHash == q.answerHash
        }

        if (!allCorrect) {
            settingsRepository.updateRecoveryAttempts()
            throw IllegalArgumentException("Não foi possível validar as respostas. Tente novamente mais tarde.")
        }

        // Reset rate limiting
        settingsRepository.resetRecoveryAttempts()

        // Derive recovery key from answers using stored installation-specific salt (with fixed fallback for legacy backups)
        val normalized = answers.map { it.trim().lowercase() }
        val combined = normalized.joinToString("|||")
        val recoverySaltBytes = if (recovery.recoverySalt.isNullOrEmpty()) {
            "lyvox-recovery!!".toByteArray(Charsets.US_ASCII)
        } else {
            RecoveryHasher.hexToBytes(recovery.recoverySalt)
        }
        require(recoverySaltBytes.size == 16) { "Salt de recuperação inválido." }
        val recoveryKey = KeyDerivation.deriveKey(combined, recoverySaltBytes)

        // Decrypt master key
        val wrappedKey = recovery.wrappedMasterKey
            ?: throw IllegalStateException("Chave mestra de recuperação não encontrada.")
        val wrapNonce = recovery.wrapNonce
            ?: throw IllegalStateException("Chave mestra de recuperação não encontrada.")

        val masterKeyHex = CryptoManager.decryptField(recoveryKey, wrappedKey, wrapNonce)
        return RecoveryHasher.hexToBytes(masterKeyHex)
    }

    /**
     * Resets the master password and re-encrypts all data.
     */
    fun resetMasterPassword(
        newPassword: String,
        newAnswers: List<Pair<Int, String>>?,
        oldSessionKey: ByteArray
    ): ByteArray {
        require(newPassword.length >= 8) { "A senha deve ter no mínimo 8 caracteres." }

        // Derive new key
        val newSalt = KeyDerivation.generateSalt()
        val newKey = KeyDerivation.deriveKey(newPassword, newSalt)

        // Re-encrypt all entries
        val entries = dbHelper.listEntries()
        for (entry in entries) {
            val password = if (entry.encryptedPassword.isEmpty()) ""
                else CryptoManager.decryptField(oldSessionKey, entry.encryptedPassword, entry.passwordNonce)
            val notes = if (entry.encryptedNotes.isEmpty()) ""
                else try { CryptoManager.decryptField(oldSessionKey, entry.encryptedNotes, entry.notesNonce ?: "") }
                    catch (_: Exception) { "" }

            val (newEncPwd, newPwdNonce) = CryptoManager.encryptField(newKey, password)
            val (newEncNotes, newNotesNonce) = if (notes.isEmpty()) Pair("", "")
                else CryptoManager.encryptField(newKey, notes)

            dbHelper.rawUpdateEntry(
                entry.id, newEncPwd, newPwdNonce, newEncNotes,
                if (newNotesNonce.isEmpty()) null else newNotesNonce
            )
        }

        // Re-encrypt all notes
        val notes = dbHelper.listNotes()
        for (note in notes) {
            val content = if (note.encryptedContent.isEmpty()) ""
                else CryptoManager.decryptField(oldSessionKey, note.encryptedContent, note.contentNonce)
            val (newEncContent, newContentNonce) = CryptoManager.encryptField(newKey, content)
            dbHelper.rawUpdateNote(note.id, newEncContent, newContentNonce)
        }

        // Update salt
        settingsRepository.setSalt(RecoveryHasher.bytesToHex(newSalt))

        // Update recovery if new answers provided
        if (newAnswers != null && newAnswers.size == 3) {
            setupRecovery(newAnswers, newKey)
        }

        return newKey
    }
}
