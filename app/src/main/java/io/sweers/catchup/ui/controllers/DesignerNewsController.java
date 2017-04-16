package io.sweers.catchup.ui.controllers;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.view.ContextThemeWrapper;
import com.bluelinelabs.conductor.Controller;
import com.squareup.moshi.Moshi;
import com.uber.autodispose.CompletableScoper;
import dagger.Binds;
import dagger.Lazy;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.android.AndroidInjector;
import dagger.multibindings.IntoMap;
import io.reactivex.Single;
import io.sweers.catchup.BuildConfig;
import io.sweers.catchup.R;
import io.sweers.catchup.data.ISOInstantAdapter;
import io.sweers.catchup.data.LinkManager;
import io.sweers.catchup.data.designernews.DesignerNewsService;
import io.sweers.catchup.data.designernews.model.StoriesResponse;
import io.sweers.catchup.data.designernews.model.Story;
import io.sweers.catchup.injection.ControllerKey;
import io.sweers.catchup.ui.base.BaseNewsController;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Qualifier;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.threeten.bp.Instant;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;

public final class DesignerNewsController extends BaseNewsController<Story> {

  @Inject DesignerNewsService service;
  @Inject LinkManager linkManager;

  public DesignerNewsController() {
    super();
  }

  public DesignerNewsController(Bundle args) {
    super(args);
  }

  @Override protected Context onThemeContext(@NonNull Context context) {
    return new ContextThemeWrapper(context, R.style.CatchUp_DesignerNews);
  }

  @Override protected void bindItemView(@NonNull Story story, @NonNull ViewHolder holder) {
    holder.title(story.title());

    holder.score(Pair.create("▲", story.voteCount()));
    holder.timestamp(story.createdAt());
    holder.author(story.userDisplayName());

    holder.source(story.hostname());

    holder.comments(story.commentCount());
    holder.tag(story.badge());

    holder.itemClicks()
        .compose(transformUrlToMeta(story.url()))
        .flatMapCompletable(linkManager)
        .to(new CompletableScoper(holder))
        .subscribe();
    holder.itemCommentClicks()
        .compose(transformUrlToMeta(story.siteUrl()
            .replace("api.", "www.")))
        .flatMapCompletable(linkManager)
        .to(new CompletableScoper(holder))
        .subscribe();
  }

  @NonNull @Override protected Single<List<Story>> getDataSingle() {
    return service.getTopStories(1)
        .map(StoriesResponse::stories);
  }

  @Subcomponent
  public interface Component extends AndroidInjector<DesignerNewsController> {

    @Subcomponent.Builder
    abstract class Builder extends AndroidInjector.Builder<DesignerNewsController> {}
  }

  @dagger.Module(subcomponents = Component.class)
  public abstract static class Module {

    @Qualifier
    private @interface InternalApi {}

    @Binds @IntoMap @ControllerKey(DesignerNewsController.class)
    abstract AndroidInjector.Factory<? extends Controller> bindDesignerNewsControllerInjectorFactory(
        Component.Builder builder);

    @Provides @InternalApi
    static OkHttpClient provideDesignerNewsOkHttpClient(OkHttpClient okHttpClient) {
      return okHttpClient.newBuilder()
          .addNetworkInterceptor(chain -> {
            Request originalRequest = chain.request();
            HttpUrl originalUrl = originalRequest.url();
            return chain.proceed(originalRequest.newBuilder()
                .url(originalUrl.newBuilder()
                    .addQueryParameter("client_id", BuildConfig.DESIGNER_NEWS_CLIENT_ID)
                    .build())
                .build());
          })
          .build();
    }

    @Provides @InternalApi static Moshi provideDesignerNewsMoshi(Moshi moshi) {
      return moshi.newBuilder()
          .add(Instant.class, new ISOInstantAdapter())
          .build();
    }

    @Provides static DesignerNewsService provideDesignerNewsService(
        @InternalApi final Lazy<OkHttpClient> client,
        @InternalApi Moshi moshi,
        RxJava2CallAdapterFactory rxJavaCallAdapterFactory) {
      Retrofit retrofit = new Retrofit.Builder().baseUrl(DesignerNewsService.ENDPOINT)
          .callFactory(request -> client.get()
              .newCall(request))
          .addCallAdapterFactory(rxJavaCallAdapterFactory)
          .addConverterFactory(MoshiConverterFactory.create(moshi))
          .build();
      return retrofit.create(DesignerNewsService.class);
    }
  }
}
