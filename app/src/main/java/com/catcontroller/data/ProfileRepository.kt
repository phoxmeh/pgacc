package com.catcontroller.data

import com.catcontroller.data.db.ProfileDao
import com.catcontroller.data.db.ProfileEntity
import com.catcontroller.model.RadioProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val dao: ProfileDao,
) {
    val profiles: Flow<List<RadioProfile>> = dao.observeAll().map { list ->
        list.map { it.toProfile() }
    }

    suspend fun getDefault(): RadioProfile? = dao.getDefault()?.toProfile()

    suspend fun save(profile: RadioProfile): Long = dao.upsert(ProfileEntity.from(profile))

    suspend fun delete(profile: RadioProfile) = dao.delete(ProfileEntity.from(profile))

    suspend fun setDefault(id: Long) {
        dao.clearDefault()
        dao.setDefault(id)
    }
}
