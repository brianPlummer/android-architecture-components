/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.example.github.repository;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.LiveDataReactiveStreams;

import com.android.example.github.api.ApiResponse;
import com.android.example.github.api.GithubService;
import com.android.example.github.db.UserDao;
import com.android.example.github.vo.Resource;
import com.android.example.github.vo.Status;
import com.android.example.github.vo.User;
import com.nytimes.android.external.store3.base.Persister;
import com.nytimes.android.external.store3.base.impl.Store;
import com.nytimes.android.external.store3.base.impl.StoreBuilder;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.functions.Function;

/**
 * Repository that handles User objects.
 */
@Singleton
public class UserRepository {

    private final Store<Resource<User>, String> userStore;

    @Inject
    UserRepository(UserDao userDao, GithubService githubService) {


        userStore = StoreBuilder.<String,User,Resource<User>>parsedWithKey()
                .fetcher(user -> githubService.getUser(user)
                        .map(userApiResponse -> userApiResponse.body))

                .persister(new Persister<User, String>() {
                    @Nonnull
                    @Override
                    public Maybe<User> read(@Nonnull String s) {
                        return Maybe.just(userDao.findByLogin(s).getValue());
                    }

                    @Nonnull
                    @Override
                    public Single<Boolean> write(@Nonnull String s, @Nonnull User user) {
                        userDao.insert(user);
                        return Single.just(true);
                    }

                })
                .parser(user -> new Resource<>(Status.SUCCESS,user,"huzzah"))
                .open();
    }

    public LiveData<Resource<User>> loadUser(String login) {
        return LiveDataReactiveStreams.fromPublisher(userStore.get(login).toFlowable());
    }
}
