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

package com.android.example.github.di;

import android.app.Application;
import android.arch.persistence.room.Room;

import com.android.example.github.api.GithubService;
import com.android.example.github.db.GithubDb;
import com.android.example.github.db.RepoDao;
import com.android.example.github.db.UserDao;
import com.android.example.github.util.LiveDataCallAdapterFactory;
import com.android.example.github.vo.Repo;
import com.android.example.github.vo.Resource;
import com.android.example.github.vo.Status;
import com.android.example.github.vo.User;
import com.nytimes.android.external.store3.base.Fetcher;
import com.nytimes.android.external.store3.base.Persister;
import com.nytimes.android.external.store3.base.impl.Store;
import com.nytimes.android.external.store3.base.impl.StoreBuilder;

import java.util.List;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import io.reactivex.Maybe;
import io.reactivex.Single;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

@Module(includes = ViewModelModule.class)
class AppModule {
    @Singleton @Provides
    GithubService provideGithubService() {
        return new Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(new LiveDataCallAdapterFactory())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build()
                .create(GithubService.class);
    }

    @Singleton @Provides
    GithubDb provideDb(Application app) {
        return Room.databaseBuilder(app, GithubDb.class,"github.db").build();
    }

    @Singleton @Provides
    UserDao provideUserDao(GithubDb db) {
        return db.userDao();
    }

    @Singleton @Provides
    RepoDao provideRepoDao(GithubDb db) {
        return db.repoDao();
    }


    @Singleton @Provides
    Store<Resource<User>, String> provideUserStore(UserDao userDao, GithubService githubService) {

        Store<Resource<User>, String> userStore = StoreBuilder.<String,User,Resource<User>>parsedWithKey()
                .fetcher(user -> githubService.getUser(user)
                        .map(userApiResponse -> userApiResponse.body))
                .persister(new Persister<User, String>() {
                    @Nonnull
                    @Override
                    public Maybe<User> read(@Nonnull String s) {
                        User user = userDao.findByLogin(s).getValue();
                        Maybe<User> userMaybe = Maybe.never();
                        if (user != null) {
                            userMaybe = Maybe.just(user);
                        }
                        return userMaybe;
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
        return userStore;
    }

    @Singleton @Provides
    Store<Resource<List<Repo>>, String> providesReposStore(GithubService githubService, RepoDao repoDao) {
        Store<Resource<List<Repo>>, String> reposStore = StoreBuilder.<String,List<Repo>,Resource<List<Repo>>>parsedWithKey()
                .fetcher(new Fetcher<List<Repo>, String>() {
                    @Nonnull
                    @Override
                    public Single<List<Repo>> fetch(@Nonnull String s) {
                        return  githubService.getRepos(s)
                                .map(userApiResponse -> userApiResponse.body);
                    }
                })
                .persister(new Persister<List<Repo>, String>() {
                    @Nonnull
                    @Override
                    public Maybe<List<Repo>> read(@Nonnull String owner) {
                        List<Repo> repos = repoDao.loadRepositories(owner).getValue();
                        Maybe<List<Repo>> listMaybe = Maybe.never();
                        if (repos != null && !repos.isEmpty()) {
                            listMaybe.just(repos);
                        }
                        return listMaybe;
                    }

                    @Nonnull
                    @Override
                    public Single<Boolean> write(@Nonnull String s, @Nonnull List<Repo> repos) {
                        repoDao.insertRepos(repos);
                        return Single.just(true);
                    }
                })
                .parser(repos -> new Resource<>(Status.SUCCESS,repos,"huzzah"))
                .open();
        return reposStore;
    }

}
