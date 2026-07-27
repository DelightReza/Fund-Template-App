package com.delightreza.fund.data

import android.util.Base64
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type
import java.net.URI

class Repository(private val dataStore: AppDataStore) {
    val pendingSyncFlow: Flow<Boolean> get() = dataStore.pendingSyncFlow
    private val api: GitHubApi

    private fun formatJsonStringLikeWebsite(rawJson: String): String {
        var jsonStr = rawJson

        // 1. Format flat arrays (like splitAmong) to single line
        jsonStr = jsonStr.replace(Regex("\\[\\n\\s+([^\\[\\]{}]+?)\\n\\s+\\]")) { match ->
            val inner = match.groupValues[1]
            val singleLine = inner.replace(Regex("\\n\\s+"), " ")
            "[$singleLine]"
        }

        // 2. Format empty arrays
        jsonStr = jsonStr.replace(Regex("\\[\\n\\s+\\]"), "[]")

        // 3. Format Person objects in config to single line
        jsonStr = jsonStr.replace(Regex("\\{\\n\\s+\"id\":\\s*\"[^\"]+\",\\n\\s+\"name\":\\s*\"[^\"]+\",\\n\\s+\"active\":\\s*(?:true|false)\\n\\s+\\}")) { match ->
            match.value.replace(Regex("\\n\\s+"), " ")
        }

        // 4. Format BillType objects in config to single line
        jsonStr = jsonStr.replace(Regex("\\{\\n\\s+\"id\":\\s*\"[^\"]+\",\\n\\s+\"name\":\\s*\"[^\"]+\",\\n\\s+\"icon\":\\s*\"[^\"]+\"\\n\\s+\\}")) { match ->
            match.value.replace(Regex("\\n\\s+"), " ")
        }

        return jsonStr
    }

    private fun formatAppConfigJson(config: AppConfig): String {
        val obj = JsonObject()
        obj.addProperty("siteTitle", config.siteTitle)
        obj.addProperty("siteSubtitle", config.siteSubtitle)
        obj.addProperty("currency", config.currency)
        obj.addProperty("repoOwner", config.repoOwner)
        obj.addProperty("repoName", config.repoName)
        obj.addProperty("repoBranch", config.repoBranch)
        obj.addProperty("dataFileName", config.dataFileName)

        val peopleArray = JsonArray()
        config.members.forEach { m ->
            val pObj = JsonObject()
            pObj.addProperty("id", m.id)
            pObj.addProperty("name", m.name)
            pObj.addProperty("active", m.active)
            peopleArray.add(pObj)
        }
        obj.add("people", peopleArray)

        val billTypesArray = JsonArray()
        config.billTypes.forEach { b ->
            val bObj = JsonObject()
            bObj.addProperty("id", b.id)
            bObj.addProperty("name", b.name)
            bObj.addProperty("icon", b.icon)
            billTypesArray.add(bObj)
        }
        obj.add("billTypes", billTypesArray)

        val rawPretty = gson.toJson(obj)
        return formatJsonStringLikeWebsite(rawPretty)
    }

    private fun resolveWhoOrBill(tx: Transaction): String {
        return when (tx.type) {
            "credit" -> tx.payerId?.takeIf { it.isNotEmpty() } ?: tx.whoOrBill
            "debit" -> tx.billTypeId?.takeIf { it.isNotEmpty() } ?: tx.whoOrBill
            else -> tx.whoOrBill
        }
    }

    private inner class TransactionSerializer : JsonSerializer<Transaction> {
        override fun serialize(src: Transaction, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()
            obj.addProperty("id", src.id)
            if (src.parentId != null) obj.addProperty("parentId", src.parentId)
            obj.addProperty("type", src.type)
            obj.addProperty("whoOrBill", resolveWhoOrBill(src))
            
            if (src.amount % 1.0 == 0.0) obj.addProperty("amount", src.amount.toInt()) 
            else obj.addProperty("amount", src.amount)
            
            obj.addProperty("note", src.note)
            obj.addProperty("date", src.date)
            
            if (!src.splitAmong.isNullOrEmpty()) {
                val array = JsonArray()
                src.splitAmong.forEach { array.add(it) }
                obj.add("splitAmong", array)
            }

            if (src.splitAmong.isNullOrEmpty() && !src.exemptions.isNullOrEmpty()) {
                val array = JsonArray()
                src.exemptions.forEach { array.add(it) }
                obj.add("exemptions", array)
            }

            if (src.distributionTotal != null) obj.addProperty("distributionTotal", src.distributionTotal)
            return obj
        }
    }

    private inner class FundDataSerializer : JsonSerializer<FundData> {
        override fun serialize(src: FundData, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()

            val peopleObj = JsonObject()
            src.people.forEach { (k, v) ->
                if (v % 1.0 == 0.0) peopleObj.addProperty(k, v.toLong())
                else peopleObj.addProperty(k, v)
            }
            obj.add("people", peopleObj)

            val billTypesObj = JsonObject()
            src.billTypes.forEach { (k, v) ->
                if (v % 1.0 == 0.0) billTypesObj.addProperty(k, v.toLong())
                else billTypesObj.addProperty(k, v)
            }
            obj.add("billTypes", billTypesObj)

            val txArray = JsonArray()
            src.transactions.forEach { tx ->
                txArray.add(context.serialize(tx, Transaction::class.java))
            }
            obj.add("transactions", txArray)

            return obj
        }
    }

    private fun formatAmount(amount: Double): String {
        return if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
    }

    private val gson = GsonBuilder()
        .registerTypeAdapter(Transaction::class.java, TransactionSerializer())
        .registerTypeAdapter(FundData::class.java, FundDataSerializer())
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        api = retrofit.create(GitHubApi::class.java)
    }

    fun getSavedRepos(): Flow<Set<String>> = dataStore.savedReposFlow

    suspend fun removeSavedRepo(url: String) {
        dataStore.removeSavedRepo(url)
    }

    suspend fun setActiveConfig(input: String, customToken: String? = null): AppConfig? = withContext(Dispatchers.IO) {
        val cleanUrl = input.trim().removeSuffix("/")
        var owner = ""
        var repo = ""
        
        if (cleanUrl.contains("github.io")) {
            val uri = URI(if (cleanUrl.startsWith("http")) cleanUrl else "https://$cleanUrl")
            val host = uri.host ?: return@withContext null
            val pathParts = uri.path.split("/").filter { it.isNotBlank() }
            if (pathParts.isEmpty()) return@withContext null
            owner = host.split(".")[0]
            repo = pathParts[0]
        } else if (cleanUrl.contains("github.com")) {
            val pathParts = URI(if (cleanUrl.startsWith("http")) cleanUrl else "https://$cleanUrl")
                .path.split("/").filter { it.isNotBlank() }
            if (pathParts.size < 2) return@withContext null
            owner = pathParts[0]
            repo = pathParts[1]
        } else if (cleanUrl.contains("/")) {
            val parts = cleanUrl.split("/").filter { it.isNotBlank() }
            if (parts.size < 2) return@withContext null
            owner = parts[0]
            repo = parts[1]
        } else {
            return@withContext null
        }

        var config: AppConfig? = null
        val authHeader = customToken?.let { "token $it" }

        if (authHeader != null) {
            try {
                val file = api.getFileDetails(authHeader, owner, repo, "config.json")
                val decoded = String(Base64.decode(file.content, Base64.DEFAULT))
                config = gson.fromJson(decoded, AppConfig::class.java)
            } catch (e: Exception) {}
        }
        
        // 2. Try Raw GitHub
        if (config == null) {
            try { config = api.fetchConfigDynamic("https://raw.githubusercontent.com/$owner/$repo/main/config.json") } catch(e: Exception) {}
        }
        
        // 3. Try GitHub Pages
        if (config == null) {
            try { config = api.fetchConfigDynamic("https://$owner.github.io/$repo/config.json") } catch(e: Exception) {}
        }

        if (config != null) {
            val fullConfig = config.copy(repoOwner = owner, repoName = repo)
            val url = "https://$owner.github.io/$repo/config.json"
            dataStore.saveConfigCache(formatAppConfigJson(fullConfig))
            dataStore.saveConfigUrl(url)
            dataStore.addSavedRepo(url, fullConfig.siteTitle)
            return@withContext fullConfig
        }
        return@withContext null
    }

    suspend fun getAppConfig(): AppConfig? = dataStore.getConfigCache()

    suspend fun fetchRemoteConfig(customToken: String? = null): AppConfig? = withContext(Dispatchers.IO) {
        val current = dataStore.getConfigCache()
        if (dataStore.hasPendingConfigSync()) {
            return@withContext current
        }

        if (current == null) return@withContext null
        val owner = current.repoOwner
        val repo = current.repoName
        if (owner.isBlank() || repo.isBlank()) return@withContext current

        val token = customToken ?: dataStore.tokenFlow.firstOrNull()
        val authHeader = if (!token.isNullOrBlank()) "token $token" else null
        val timestamp = System.currentTimeMillis()

        var fetched: AppConfig? = null

        if (authHeader != null) {
            try {
                val file = api.getFileDetails(authHeader, owner, repo, "config.json")
                val decoded = String(Base64.decode(file.content, Base64.DEFAULT))
                fetched = gson.fromJson(decoded, AppConfig::class.java)
            } catch (e: Exception) {
                // Ignore network error
            }
        }

        if (fetched == null) {
            try {
                fetched = api.fetchConfigDynamic("https://raw.githubusercontent.com/$owner/$repo/main/config.json?t=$timestamp")
            } catch (e: Exception) {
                // Ignore network error
            }
        }

        if (fetched == null) {
            try {
                fetched = api.fetchConfigDynamic("https://$owner.github.io/$repo/config.json?t=$timestamp")
            } catch (e: Exception) {
                // Ignore network error
            }
        }

        if (fetched != null) {
            val fullConfig = fetched.copy(repoOwner = owner, repoName = repo)
            dataStore.saveConfigCache(formatAppConfigJson(fullConfig))
            return@withContext fullConfig
        }
        return@withContext current
    }

    suspend fun saveLocalConfig(newConfig: AppConfig) = withContext(Dispatchers.IO) {
        dataStore.saveConfigCache(formatAppConfigJson(newConfig))
    }

    suspend fun updateRemoteConfig(
        token: String, 
        newConfig: AppConfig, 
        commitMessage: String = "Update Configuration"
    ): Boolean = withContext(Dispatchers.IO) {
        val current = getAppConfig() ?: newConfig
        val owner = current.repoOwner.ifEmpty { newConfig.repoOwner }
        val repo = current.repoName.ifEmpty { newConfig.repoName }
        if (owner.isBlank() || repo.isBlank()) return@withContext false

        val fullConfig = newConfig.copy(repoOwner = owner, repoName = repo)
        val jsonToCommit = formatAppConfigJson(fullConfig)

        if (token.isNotBlank()) {
            try {
                val authHeader = "token $token"
                val fileDetails = api.getFileDetails(authHeader, owner, repo, "config.json")

                val remoteContentDecoded = String(Base64.decode(fileDetails.content.replace("\n", "").replace("\r", ""), Base64.DEFAULT)).trim()
                if (remoteContentDecoded == jsonToCommit.trim()) {
                    dataStore.saveConfigCache(jsonToCommit)
                    dataStore.setPendingConfigSync(false)
                    return@withContext true
                }

                val encodedContent = Base64.encodeToString(jsonToCommit.toByteArray(), Base64.NO_WRAP)
                val request = UpdateFileRequest(message = commitMessage, content = encodedContent, sha = fileDetails.sha)

                api.updateFile(authHeader, owner, repo, "config.json", request)
                dataStore.saveConfigCache(jsonToCommit)
                dataStore.setPendingConfigSync(false)
                return@withContext true
            } catch (e: Exception) {
                // Offline fallback
            }
        }

        dataStore.saveConfigCache(jsonToCommit)
        dataStore.setPendingConfigSync(true)
        true
    }

    suspend fun saveLocalDataAndMarkPending(data: FundData, msg: String? = null) = withContext(Dispatchers.IO) {
        val config = getAppConfig()
        if (config != null) {
            val billTotals = LinkedHashMap<String, Double>()
            data.billTypes.keys.forEach { billTotals[it] = 0.0 }
            config.billTypes.forEach { bt -> if (!billTotals.containsKey(bt.id)) billTotals[bt.id] = 0.0 }

            data.transactions.forEach { tx ->
                if (tx.type == "debit") {
                    val btId = resolveWhoOrBill(tx)
                    if (btId.isNotEmpty()) billTotals[btId] = (billTotals[btId] ?: 0.0) + tx.amount
                }
            }
            val activeBillTotals = billTotals.filterValues { it > 0.0 }
            data.billTypes.clear(); data.billTypes.putAll(activeBillTotals)

            val peopleTotals = LinkedHashMap<String, Double>()
            data.people.keys.forEach { peopleTotals[it] = 0.0 }
            config.members.forEach { m -> if (!peopleTotals.containsKey(m.id)) peopleTotals[m.id] = 0.0 }

            data.transactions.forEach { tx ->
                if (tx.type == "credit") {
                    val pid = resolveWhoOrBill(tx)
                    if (pid.isNotEmpty()) peopleTotals[pid] = (peopleTotals[pid] ?: 0.0) + tx.amount
                }
            }
            val activePeopleTotals = peopleTotals.filterValues { it > 0.0 }
            data.people.clear(); data.people.putAll(activePeopleTotals)
        }

        val rawJson = formatJsonStringLikeWebsite(gson.toJson(data))
        dataStore.saveCache(rawJson)
        dataStore.setPendingDataSync(true, msg)
    }

    suspend fun syncPendingData(customToken: String? = null): Boolean = withContext(Dispatchers.IO) {
        val token = customToken ?: dataStore.tokenFlow.firstOrNull()
        var success = true

        if (dataStore.hasPendingConfigSync()) {
            val localConfig = getAppConfig()
            if (localConfig != null && !token.isNullOrBlank()) {
                val configSuccess = updateRemoteConfig(token, localConfig, "Sync offline configuration updates")
                if (!configSuccess) success = false
            }
        }

        if (dataStore.hasPendingDataSync()) {
            if (!token.isNullOrBlank()) {
                val localData = getCachedData()
                if (localData != null) {
                    val ctx = getLatestDataAndContext(token)
                    if (ctx != null) {
                        val (authHeader, sha, remoteData) = ctx
                        
                        val remoteIds = remoteData.transactions.map { it.id }.toSet()
                        val pendingTxs = localData.transactions.filter { !remoteIds.contains(it.id) }
                        
                        val finalTxList = remoteData.transactions.toMutableList()
                        pendingTxs.reversed().forEach { tx ->
                            finalTxList.add(0, tx)
                        }
                        remoteData.transactions.clear()
                        remoteData.transactions.addAll(finalTxList)

                        val msg = dataStore.getPendingCommitMsg() ?: "Sync offline transactions"
                        try {
                            commitData(authHeader, remoteData, sha, msg)
                            dataStore.setPendingDataSync(false)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            success = false
                        }
                    } else {
                        success = false
                    }
                }
            } else {
                success = false
            }
        }

        return@withContext success
    }

    suspend fun getCachedData(): FundData? = withContext(Dispatchers.IO) {
        val json = dataStore.getCache()
        if (!json.isNullOrEmpty()) {
            try { return@withContext gson.fromJson(json, FundData::class.java) } catch (e: Exception) {}
        }
        return@withContext null
    }

    suspend fun fetchData(): FundData? = withContext(Dispatchers.IO) {
        val token = dataStore.tokenFlow.firstOrNull()

        if (dataStore.hasPendingDataSync() || dataStore.hasPendingConfigSync()) {
            syncPendingData(token)
        }

        val config = fetchRemoteConfig() ?: getAppConfig() ?: return@withContext getCachedData()
        val authHeader = if (!token.isNullOrBlank()) "token $token" else null
        val timestamp = System.currentTimeMillis()
        
        var data: FundData? = null
        
        // 1. Try API
        if (authHeader != null) {
            try {
                val file = api.getFileDetails(authHeader, config.repoOwner, config.repoName, config.dataFileName)
                val json = String(Base64.decode(file.content, Base64.DEFAULT))
                data = gson.fromJson(json, FundData::class.java)
            } catch (e: Exception) {}
        }
        
        // 2. Try Raw fallback
        if (data == null) {
            try { data = api.fetchFundDataDynamic("https://raw.githubusercontent.com/${config.repoOwner}/${config.repoName}/main/${config.dataFileName}?t=$timestamp") } catch (e: Exception) {}
        }
        
        // 3. Try Pages fallback
        if (data == null) {
            try { data = api.fetchFundDataDynamic("https://${config.repoOwner}.github.io/${config.repoName}/${config.dataFileName}?t=$timestamp") } catch (e: Exception) {}
        }
        
        if (data != null) {
            dataStore.saveCache(gson.toJson(data))
            return@withContext data
        }
        return@withContext getCachedData()
    }

    suspend fun verifyToken(token: String): Boolean = withContext(Dispatchers.IO) {
        val config = getAppConfig() ?: return@withContext false
        try {
            api.getFileDetails("token $token", config.repoOwner, config.repoName, config.dataFileName)
            true
        } catch (e: Exception) { false }
    }

    private suspend fun getLatestDataAndContext(token: String): Triple<String, String?, FundData>? {
        val config = getAppConfig() ?: return null
        val authHeader = "token $token"
        try {
            val fileDetails = api.getFileDetails(authHeader, config.repoOwner, config.repoName, config.dataFileName)
            val currentJson = String(Base64.decode(fileDetails.content, Base64.DEFAULT))
            val currentData = gson.fromJson(currentJson, FundData::class.java)
            return Triple(authHeader, fileDetails.sha, currentData)
        } catch (e: HttpException) {
            if (e.code() == 404) return Triple(authHeader, null, FundData())
            return null
        } catch (e: Exception) { return null }
    }

    private suspend fun commitData(authHeader: String, data: FundData, sha: String?, msg: String) {
        val config = getAppConfig() ?: return
        
        val billTotals = LinkedHashMap<String, Double>()
        data.billTypes.keys.forEach { billTotals[it] = 0.0 }
        config.billTypes.forEach { bt -> if (!billTotals.containsKey(bt.id)) billTotals[bt.id] = 0.0 }

        data.transactions.forEach { tx ->
            if (tx.type == "debit") {
                val btId = resolveWhoOrBill(tx)
                if (btId.isNotEmpty()) billTotals[btId] = (billTotals[btId] ?: 0.0) + tx.amount
            }
        }
        val activeBillTotals = billTotals.filterValues { it > 0.0 }
        data.billTypes.clear(); data.billTypes.putAll(activeBillTotals)

        val peopleTotals = LinkedHashMap<String, Double>()
        data.people.keys.forEach { peopleTotals[it] = 0.0 }
        config.members.forEach { m -> if (!peopleTotals.containsKey(m.id)) peopleTotals[m.id] = 0.0 }

        data.transactions.forEach { tx ->
            if (tx.type == "credit") {
                val pid = resolveWhoOrBill(tx)
                if (pid.isNotEmpty()) peopleTotals[pid] = (peopleTotals[pid] ?: 0.0) + tx.amount
            }
        }
        val activePeopleTotals = peopleTotals.filterValues { it > 0.0 }
        data.people.clear(); data.people.putAll(activePeopleTotals)
        
        val rawJson = formatJsonStringLikeWebsite(gson.toJson(data))

        if (sha != null) {
            try {
                val fileDetails = api.getFileDetails(authHeader, config.repoOwner, config.repoName, config.dataFileName)
                val remoteDecoded = String(Base64.decode(fileDetails.content.replace("\n", "").replace("\r", ""), Base64.DEFAULT)).trim()
                if (remoteDecoded == rawJson.trim()) {
                    dataStore.saveCache(rawJson)
                    return
                }
            } catch (e: Exception) {
                // Ignore check failure and proceed with commit
            }
        }

        val encodedContent = Base64.encodeToString(rawJson.toByteArray(), Base64.NO_WRAP)
        val request = UpdateFileRequest(message = msg, content = encodedContent, sha = sha)
        
        api.updateFile(authHeader, config.repoOwner, config.repoName, config.dataFileName, request)
        dataStore.saveCache(rawJson)
        dataStore.setPendingDataSync(false)
    }

    suspend fun addTransaction(token: String, newTx: Transaction): Boolean = withContext(Dispatchers.IO) {
        val personName = getAppConfig()?.members?.find { it.id == newTx.whoOrBill }?.name ?: newTx.whoOrBill
        val billName = getAppConfig()?.billTypes?.find { it.id == newTx.whoOrBill }?.name ?: newTx.whoOrBill
        val currency = getAppConfig()?.currency ?: ""
        val amtStr = formatAmount(newTx.amount)
        
        val msg = if (newTx.type == "credit") {
            val noteStr = if (newTx.note.isNotEmpty()) " for ${newTx.note}" else ""
            "Credit: $personName added $currency$amtStr$noteStr"
        } else {
            val noteStrP = if (newTx.note.isNotEmpty()) " (${newTx.note})" else ""
            val splitCount = newTx.splitAmong?.size ?: 0
            "Debit: $currency$amtStr used for $billName$noteStrP - Split among $splitCount"
        }

        if (token.isNotBlank()) {
            val ctx = getLatestDataAndContext(token)
            if (ctx != null) {
                val (authHeader, sha, currentData) = ctx
                currentData.transactions.add(0, newTx)
                try {
                    commitData(authHeader, currentData, sha, msg)
                    dataStore.setPendingDataSync(false)
                    return@withContext true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val localData = getCachedData() ?: FundData()
        localData.transactions.removeAll { it.id == newTx.id }
        localData.transactions.add(0, newTx)
        saveLocalDataAndMarkPending(localData, msg)
        true
    }

    suspend fun addQuickExpense(token: String, payerId: String, billTypeId: String, amount: Double, note: String, date: String, exemptions: List<String>): Boolean = withContext(Dispatchers.IO) {
        val config = getAppConfig() ?: return@withContext false
        val parentId = "tx_exp_${System.currentTimeMillis()}"
        val payerName = config.members.find { it.id == payerId }?.name ?: payerId
        val billName = config.billTypes.find { it.id == billTypeId }?.name ?: billTypeId
        val referenceName = if (note.isNotEmpty()) note else billName

        val creditTx = Transaction(
            id = "${parentId}_credit", type = "credit",
            whoOrBill = payerId, payerId = payerId, amount = amount,
            note = "$payerName paid for $referenceName", date = date, parentId = parentId
        )

        val activeMembers = config.members.filter { it.active }.map { it.id }
        val splitAmong = activeMembers.filter { !exemptions.contains(it) }

        val debitTx = Transaction(
            id = "${parentId}_debit", type = "debit",
            whoOrBill = billTypeId, billTypeId = billTypeId, amount = amount,
            note = "$referenceName is paid by $payerName", date = date, parentId = parentId,
            splitAmong = splitAmong
        )

        val currency = config.currency
        val amtStr = formatAmount(amount)
        val noteStr = if (note.isNotEmpty()) " ($note)" else ""
        val msg = "Expense: $payerName paid $currency$amtStr for $billName$noteStr - Split among ${splitAmong.size}"

        if (token.isNotBlank()) {
            val ctx = getLatestDataAndContext(token)
            if (ctx != null) {
                val (authHeader, sha, currentData) = ctx
                currentData.transactions.add(0, creditTx)
                currentData.transactions.add(0, debitTx)
                try {
                    commitData(authHeader, currentData, sha, msg)
                    dataStore.setPendingDataSync(false)
                    return@withContext true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val localData = getCachedData() ?: FundData()
        localData.transactions.add(0, creditTx)
        localData.transactions.add(0, debitTx)
        saveLocalDataAndMarkPending(localData, msg)
        true
    }

    suspend fun editQuickExpense(token: String, parentId: String, payerId: String, billTypeId: String, amount: Double, note: String, date: String, exemptions: List<String>): Boolean = withContext(Dispatchers.IO) {
        val config = getAppConfig() ?: return@withContext false
        val payerName = config.members.find { it.id == payerId }?.name ?: payerId
        val billName = config.billTypes.find { it.id == billTypeId }?.name ?: billTypeId
        val referenceName = if (note.isNotEmpty()) note else billName
        val activeMembers = config.members.filter { it.active }.map { it.id }
        val splitAmong = activeMembers.filter { !exemptions.contains(it) }

        val currency = config.currency
        val amtStr = formatAmount(amount)
        val noteStr = if (note.isNotEmpty()) " ($note)" else ""
        val msg = "Edited Expense: $payerName paid $currency$amtStr for $billName$noteStr"

        fun applyExpenseEdit(data: FundData): Boolean {
            val creditIndex = data.transactions.indexOfFirst { it.parentId == parentId && it.type == "credit" }
            val debitIndex = data.transactions.indexOfFirst { it.parentId == parentId && it.type == "debit" }
            if (creditIndex == -1 || debitIndex == -1) return false

            val creditTx = data.transactions[creditIndex].copy(
                whoOrBill = payerId, payerId = payerId, amount = amount,
                note = "$payerName paid for $referenceName", date = date
            )
            val debitTx = data.transactions[debitIndex].copy(
                whoOrBill = billTypeId, billTypeId = billTypeId, amount = amount,
                note = "$referenceName is paid by $payerName", date = date,
                splitAmong = splitAmong
            )
            data.transactions[creditIndex] = creditTx
            data.transactions[debitIndex] = debitTx
            return true
        }

        if (token.isNotBlank()) {
            val ctx = getLatestDataAndContext(token)
            if (ctx != null) {
                val (authHeader, sha, currentData) = ctx
                if (applyExpenseEdit(currentData)) {
                    try {
                        commitData(authHeader, currentData, sha, msg)
                        dataStore.setPendingDataSync(false)
                        return@withContext true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        val localData = getCachedData() ?: FundData()
        if (applyExpenseEdit(localData)) {
            saveLocalDataAndMarkPending(localData, msg)
            true
        } else false
    }

    suspend fun addDistribution(
        token: String,
        totalAmount: Double,
        note: String,
        date: String,
        participantIds: List<String> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        val config = getAppConfig() ?: return@withContext false
        val activeMembers = config.members.filter { it.active }.map { it.id }
        val distributionMembers = if (participantIds.isNotEmpty()) {
            participantIds.filter { activeMembers.contains(it) }
        } else {
            activeMembers
        }
        if (distributionMembers.isEmpty()) return@withContext false
        
        val splitAmount = totalAmount / distributionMembers.size
        val parentId = "tx_dist_${System.currentTimeMillis()}"
        val newTxs = distributionMembers.mapIndexed { index, personId ->
            Transaction(
                id = "${parentId}_$index", type = "credit", 
                payerId = personId, whoOrBill = personId,
                note = note.ifEmpty { "Distribution" }, amount = splitAmount, 
                date = date, parentId = parentId, distributionTotal = totalAmount
            )
        }
        val amtStr = formatAmount(totalAmount)
        val noteStr = if (note.isNotEmpty()) " for $note" else ""
        val msg = "Distributed ${config.currency}$amtStr among ${distributionMembers.size} people$noteStr"

        if (token.isNotBlank()) {
            val ctx = getLatestDataAndContext(token)
            if (ctx != null) {
                val (authHeader, sha, currentData) = ctx
                newTxs.forEach { currentData.transactions.add(0, it) }
                try {
                    commitData(authHeader, currentData, sha, msg)
                    dataStore.setPendingDataSync(false)
                    return@withContext true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val localData = getCachedData() ?: FundData()
        newTxs.forEach { localData.transactions.add(0, it) }
        saveLocalDataAndMarkPending(localData, msg)
        true
    }

    suspend fun saveDataToRemote(token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val data = getCachedData() ?: fetchData() ?: return@withContext false
            val ctx = getLatestDataAndContext(token) ?: return@withContext false
            val (authHeader, sha, _) = ctx
            val timeStr = java.text.SimpleDateFormat("M/d/yyyy, h:mm:ss a", java.util.Locale.US).format(java.util.Date())
            commitData(authHeader, data, sha, "Update fund data - $timeStr")
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun saveConfigToRemote(token: String): Boolean = withContext(Dispatchers.IO) {
        val currentConfig = getAppConfig() ?: return@withContext false
        val timeStr = java.text.SimpleDateFormat("M/d/yyyy, h:mm:ss a", java.util.Locale.US).format(java.util.Date())
        updateRemoteConfig(token, currentConfig, "Update config - $timeStr")
    }

    suspend fun addSettlement(token: String, payerId: String, receiverId: String, amount: Double, note: String, date: String): Boolean = withContext(Dispatchers.IO) {
        val parentId = "tx_set_${System.currentTimeMillis()}"
        val payerName = getAppConfig()?.members?.find { it.id == payerId }?.name ?: payerId
        val receiverName = getAppConfig()?.members?.find { it.id == receiverId }?.name ?: receiverId
        val currency = getAppConfig()?.currency ?: ""

        val payerTx = Transaction(
            id = "${parentId}_payer", type = "credit", 
            payerId = payerId, whoOrBill = payerId,
            note = "Settlement to $receiverName" + (if(note.isNotEmpty()) ": $note" else ""),
            amount = amount, date = date, parentId = parentId
        )
        val receiverTx = Transaction(
            id = "${parentId}_rcvr", type = "credit", 
            payerId = receiverId, whoOrBill = receiverId,
            note = "Settlement from $payerName" + (if(note.isNotEmpty()) ": $note" else ""),
            amount = -amount, date = date, parentId = parentId
        )
        
        val amtStr = formatAmount(amount)
        val msg = "Settlement: $payerName paid $currency$amtStr to $receiverName"

        if (token.isNotBlank()) {
            val ctx = getLatestDataAndContext(token)
            if (ctx != null) {
                val (authHeader, sha, currentData) = ctx
                currentData.transactions.add(0, payerTx)
                currentData.transactions.add(0, receiverTx)
                try {
                    commitData(authHeader, currentData, sha, msg)
                    dataStore.setPendingDataSync(false)
                    return@withContext true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val localData = getCachedData() ?: FundData()
        localData.transactions.add(0, payerTx)
        localData.transactions.add(0, receiverTx)
        saveLocalDataAndMarkPending(localData, msg)
        true
    }

    suspend fun addTransfer(token: String, senderId: String, recipientId: String, amount: Double, note: String, date: String): Boolean = withContext(Dispatchers.IO) {
        val parentId = "tx_trf_${System.currentTimeMillis()}"
        val senderName = getAppConfig()?.members?.find { it.id == senderId }?.name ?: senderId
        val recipientName = getAppConfig()?.members?.find { it.id == recipientId }?.name ?: recipientId
        val currency = getAppConfig()?.currency ?: ""

        val senderTx = Transaction(
            id = "${parentId}_send", type = "credit", 
            payerId = senderId, whoOrBill = senderId,
            note = "Transfer to $recipientName" + (if(note.isNotEmpty()) ": $note" else ""),
            amount = -amount, date = date, parentId = parentId
        )
        val recipientTx = Transaction(
            id = "${parentId}_rcpt", type = "credit", 
            payerId = recipientId, whoOrBill = recipientId,
            note = "Transfer from $senderName" + (if(note.isNotEmpty()) ": $note" else ""),
            amount = amount, date = date, parentId = parentId
        )
        
        val amtStr = formatAmount(amount)
        val msg = "Transfer: $senderName transferred $currency$amtStr to $recipientName"

        if (token.isNotBlank()) {
            val ctx = getLatestDataAndContext(token)
            if (ctx != null) {
                val (authHeader, sha, currentData) = ctx
                currentData.transactions.add(0, senderTx)
                currentData.transactions.add(0, recipientTx)
                try {
                    commitData(authHeader, currentData, sha, msg)
                    dataStore.setPendingDataSync(false)
                    return@withContext true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val localData = getCachedData() ?: FundData()
        localData.transactions.add(0, senderTx)
        localData.transactions.add(0, recipientTx)
        saveLocalDataAndMarkPending(localData, msg)
        true
    }

    suspend fun deleteTransaction(token: String, transactionId: String, reason: String? = null): Boolean = withContext(Dispatchers.IO) {
        val config = getAppConfig()
        val currency = config?.currency ?: ""
        val reasonStr = reason?.trim() ?: ""

        fun applyDelete(data: FundData): Pair<Boolean, String> {
            val targetTx = data.transactions.find { it.id == transactionId } ?: return Pair(false, "")
            val parentId = targetTx.parentId
            val isExpenseGroup = parentId != null && parentId.startsWith("tx_exp")

            val toDelete = if (parentId != null) 
                data.transactions.filter { it.parentId == parentId } 
            else listOf(targetTx)

            data.transactions.removeAll(toDelete)

            val msg = if (isExpenseGroup) {
                val creditTx = toDelete.find { it.type == "credit" } ?: targetTx
                val debitTx = toDelete.find { it.type == "debit" } ?: targetTx
                
                val payerId = creditTx.whoOrBill.ifEmpty { creditTx.payerId ?: "" }
                val billTypeId = debitTx.whoOrBill.ifEmpty { debitTx.billTypeId ?: "" }
                
                val personName = config?.members?.find { it.id == payerId }?.name ?: payerId
                val billName = config?.billTypes?.find { it.id == billTypeId }?.name ?: billTypeId
                val amtStr = formatAmount(targetTx.amount)

                val rStr = if (reasonStr.isNotEmpty()) " ($reasonStr)" else ""
                "Deleted Expense: $personName paid $currency$amtStr for $billName$rStr"
            } else if (targetTx.type == "credit") {
                val personName = config?.members?.find { it.id == targetTx.whoOrBill }?.name ?: targetTx.whoOrBill
                val amtStr = formatAmount(targetTx.amount)
                val rStr = if (reasonStr.isNotEmpty()) " - $reasonStr" else ""
                "Deleted Credit: $personName ($currency$amtStr)$rStr"
            } else {
                val billName = config?.billTypes?.find { it.id == targetTx.whoOrBill }?.name ?: targetTx.whoOrBill
                val amtStr = formatAmount(targetTx.amount)
                val rStr = if (reasonStr.isNotEmpty()) " - $reasonStr" else ""
                "Deleted Debit: $billName ($currency$amtStr)$rStr"
            }
            return Pair(true, msg)
        }

        if (token.isNotBlank()) {
            val ctx = getLatestDataAndContext(token)
            if (ctx != null) {
                val (authHeader, sha, currentData) = ctx
                val (success, msg) = applyDelete(currentData)
                if (success) {
                    try {
                        commitData(authHeader, currentData, sha, msg)
                        dataStore.setPendingDataSync(false)
                        return@withContext true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        val localData = getCachedData() ?: FundData()
        val (success, msg) = applyDelete(localData)
        if (success) {
            saveLocalDataAndMarkPending(localData, msg)
            true
        } else false
    }

    suspend fun editTransaction(token: String, updatedTx: Transaction): Boolean = withContext(Dispatchers.IO) {
        val config = getAppConfig()
        val currency = config?.currency ?: ""

        fun applyEdit(data: FundData): Pair<Boolean, String> {
            val index = data.transactions.indexOfFirst { it.id == updatedTx.id }
            if (index == -1) return Pair(false, "")
            val oldTx = data.transactions[index]

            val parentId = updatedTx.parentId ?: oldTx.parentId
            val targetTx = updatedTx.copy(parentId = parentId)

            val isExpenseGroup = parentId != null && parentId.startsWith("tx_exp")

            if (isExpenseGroup) {
                val linkedIndex = data.transactions.indexOfFirst { it.parentId == parentId && it.id != updatedTx.id }
                val linkedTx = if (linkedIndex != -1) data.transactions[linkedIndex] else null

                val creditTx = if (targetTx.type == "credit") targetTx else linkedTx
                val debitTx = if (targetTx.type == "debit") targetTx else linkedTx

                val payerId = creditTx?.whoOrBill?.ifEmpty { creditTx.payerId ?: "" } ?: creditTx?.payerId ?: ""
                val billTypeId = debitTx?.whoOrBill?.ifEmpty { debitTx.billTypeId ?: "" } ?: debitTx?.billTypeId ?: ""

                val payerName = config?.members?.find { it.id == payerId }?.name ?: payerId
                val billName = config?.billTypes?.find { it.id == billTypeId }?.name ?: billTypeId
                val amtStr = formatAmount(targetTx.amount)

                fun extractCleanNote(n: String?): String {
                    if (n.isNullOrEmpty()) return ""
                    if (n.contains(" paid for ") || n.contains(" is paid by ")) return ""
                    return n
                }

                val cleanNote = extractCleanNote(targetTx.note).ifEmpty { extractCleanNote(linkedTx?.note) }
                val referenceName = if (cleanNote.isNotEmpty()) cleanNote else billName

                val creditNote = "$payerName paid for $referenceName"
                val debitNote = "$referenceName is paid by $payerName"

                val finalUpdatedTx = targetTx.copy(
                    parentId = parentId,
                    note = if (targetTx.type == "credit") creditNote else debitNote
                )
                data.transactions[index] = finalUpdatedTx

                if (linkedIndex != -1 && linkedTx != null) {
                    val newLinkedTx = linkedTx.copy(
                        parentId = parentId,
                        amount = targetTx.amount,
                        date = targetTx.date,
                        note = if (linkedTx.type == "credit") creditNote else debitNote
                    )
                    data.transactions[linkedIndex] = newLinkedTx
                }

                val noteStr = if (cleanNote.isNotEmpty()) " ($cleanNote)" else ""
                val msg = "Edited Expense: $payerName paid $currency$amtStr for $billName$noteStr"
                return Pair(true, msg)
            }

            data.transactions[index] = targetTx
            val amtStr = formatAmount(targetTx.amount)
            val msg = if (targetTx.type == "credit") {
                val personName = config?.members?.find { it.id == targetTx.whoOrBill }?.name ?: targetTx.whoOrBill
                val noteStr = if (targetTx.note.isNotEmpty()) " for ${targetTx.note}" else ""
                "Edited Credit: $personName added $currency$amtStr$noteStr"
            } else {
                val billName = config?.billTypes?.find { it.id == targetTx.whoOrBill }?.name ?: targetTx.whoOrBill
                val noteStr = if (targetTx.note.isNotEmpty()) " (${targetTx.note})" else ""
                "Edited Debit: $currency$amtStr used for $billName$noteStr"
            }

            return Pair(true, msg)
        }

        if (token.isNotBlank()) {
            val ctx = getLatestDataAndContext(token)
            if (ctx != null) {
                val (authHeader, sha, currentData) = ctx
                val (success, msg) = applyEdit(currentData)
                if (success) {
                    try {
                        commitData(authHeader, currentData, sha, msg)
                        dataStore.setPendingDataSync(false)
                        return@withContext true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        val localData = getCachedData() ?: FundData()
        val (success, msg) = applyEdit(localData)
        if (success) {
            saveLocalDataAndMarkPending(localData, msg)
            true
        } else false
    }

    suspend fun calculateBalances(data: FundData): Map<String, Double> {
        val config = dataStore.getConfigCache() ?: return emptyMap()
        val balances = config.members.associate { it.id to 0.0 }.toMutableMap()
        
        data.transactions.forEach { tx ->
            if (tx.type == "credit") {
                val pid = tx.payerId ?: tx.whoOrBill
                balances[pid] = (balances[pid] ?: 0.0) + tx.amount
            } else {
                val payers = if (!tx.splitAmong.isNullOrEmpty()) {
                    tx.splitAmong
                } else {
                    val exemptions = tx.exemptions ?: emptyList()
                    config.members.map { it.id }.filter { !exemptions.contains(it) }
                }
                
                if (payers.isNotEmpty()) {
                    val splitAmount = tx.amount / payers.size
                    payers.forEach { pid ->
                        balances[pid] = (balances[pid] ?: 0.0) - splitAmount
                    }
                }
            }
        }
        return balances
    }

    fun calculateDebtSettlements(balances: Map<String, Double>): List<Settlement> {
        val creditors = balances.filter { it.value > 0.01 }.map { it.key to it.value }.toMutableList()
        val debtors = balances.filter { it.value < -0.01 }.map { it.key to -it.value }.toMutableList()
        
        creditors.sortByDescending { it.second }
        debtors.sortByDescending { it.second }
        
        val settlements = mutableListOf<Settlement>()
        var i = 0
        var j = 0
        
        while (i < debtors.size && j < creditors.size) {
            val debtor = debtors[i]
            val creditor = creditors[j]
            
            val amount = kotlin.math.min(debtor.second, creditor.second)
            settlements.add(Settlement(from = debtor.first, to = creditor.first, amount = amount))
            
            debtors[i] = debtor.copy(second = debtor.second - amount)
            creditors[j] = creditor.copy(second = creditor.second - amount)
            
            if (debtors[i].second < 0.01) i++
            if (creditors[j].second < 0.01) j++
        }
        return settlements
    }

    suspend fun getRecentCommits(token: String): List<GitHubCommitResponse> {
        val config = dataStore.getConfigCache()
        val owner = config?.repoOwner ?: return emptyList()
        val repo = config?.repoName ?: return emptyList()
        val branch = config?.repoBranch ?: "main"
        
        return try {
            api.getCommits("Bearer $token", owner, repo, branch)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun resetCommit(token: String, sha: String) {
        val config = dataStore.getConfigCache()
        val owner = config?.repoOwner ?: return
        val repo = config?.repoName ?: return
        val branch = config?.repoBranch ?: "main"
        
        api.updateRef("Bearer $token", owner, repo, branch, UpdateRefRequest(sha, force = true))

        // Refresh local cache with contents from reset commit
        fetchRemoteConfig(token)
        fetchData()
    }
}
