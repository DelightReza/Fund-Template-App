package com.delightreza.fund.data

import com.google.gson.annotations.SerializedName

data class AppConfig(
    val siteTitle: String = "Fund",
    val siteSubtitle: String = "Expense Tracker",
    val currency: String = "₹",
    
    val repoOwner: String = "",
    val repoName: String = "",
    val repoBranch: String = "main",
    val dataFileName: String = "data.json",
    
    @SerializedName("people")
    val members: List<MemberConfig> = emptyList(),
    
    @SerializedName("billTypes")
    val billTypes: List<BillTypeConfig> = emptyList()
)

data class MemberConfig(
    val id: String,
    val name: String,
    val active: Boolean = true
)

data class BillTypeConfig(
    val id: String,
    val name: String,
    val icon: String,
    val active: Boolean = true
)

data class FundData(
    val billTypes: MutableMap<String, Double> = mutableMapOf(),
    val people: MutableMap<String, Double> = mutableMapOf(), 
    val transactions: MutableList<Transaction> = mutableListOf()
)

data class Transaction(
    val id: String,
    val type: String,
    
    val payerId: String? = null,
    val billTypeId: String? = null,
    val splitAmong: List<String>? = null,
    
    var whoOrBill: String = "", 
    
    val note: String,
    val amount: Double,
    val date: String,
    
    val exemptions: List<String>? = null,
    
    val parentId: String? = null,
    val distributionTotal: Double? = null
)

data class GitHubFileResponse(val sha: String, val content: String)

data class UpdateFileRequest(
    val message: String,
    val content: String,
    val sha: String? 
)

data class Settlement(
    val from: String,
    val to: String,
    val amount: Double
)

data class GitHubCommitResponse(
    val sha: String,
    val commit: CommitDetails
)

data class CommitDetails(
    val message: String,
    val author: CommitAuthor
)

data class CommitAuthor(
    val name: String,
    val date: String
)

data class UpdateRefRequest(
    val sha: String,
    val force: Boolean = true
)
