package com.my.parking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.parking.sharing.FamilySharingRepository
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Coordinates sharing work; network and credentials remain in the repository. */
class FamilySharingViewModel(private val repository: FamilySharingRepository) : ViewModel() {
    fun execute(action: String, data: JSONObject, done: (JSONObject) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                when(action) {
                    "familyCreate" -> repository.connect(data.getString("label"))
                    "familyJoin" -> repository.connect(data.getString("label"),data.getString("code"))
                    "familyInvite" -> repository.invite()
                    "familyRevoke" -> repository.revoke(data.getString("deviceId"))
                    "familyLeave" -> repository.leave()
                    "familySync" -> repository.sync()
                    "familyMore" -> repository.more()
                    else -> error("지원하지 않는 요청입니다.")
                }
            }
            done(result.fold({ JSONObject().put("ok",true).put("data",it) },{ JSONObject().put("ok",false).put("error",it.message ?: "공유 요청에 실패했습니다.") }))
        }
    }
}
