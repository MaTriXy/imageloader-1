package com.guardanis.imageloader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.ImageView;

import com.guardanis.imageloader.filters.BitmapBlurFilter;
import com.guardanis.imageloader.filters.BitmapCenterCropFilter;
import com.guardanis.imageloader.filters.BitmapCircularCropFilter;
import com.guardanis.imageloader.filters.BitmapColorFilter;
import com.guardanis.imageloader.filters.BitmapColorOverlayFilter;
import com.guardanis.imageloader.filters.BitmapColorOverrideFilter;
import com.guardanis.imageloader.filters.BitmapColorReplacementFilter;
import com.guardanis.imageloader.filters.BitmapRotationFilter;
import com.guardanis.imageloader.filters.ImageFilter;
import com.guardanis.imageloader.transitions.TransitionController;
import com.guardanis.imageloader.transitions.modules.FadingTransitionModule;
import com.guardanis.imageloader.transitions.modules.RotationTransitionModule;
import com.guardanis.imageloader.transitions.modules.ScalingTransitionModule;
import com.guardanis.imageloader.transitions.modules.TransitionModule;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImageRequest<V extends View> implements Runnable {

    public interface ImageSuccessCallback {
        public void onImageReady(ImageRequest request, Bitmap result);
    }

    public interface ImageErrorCallback {
        public void onImageLoadingFailure(ImageRequest request, @Nullable Throwable e);
    }

    protected static final int DEFAULT_BLUR_RADIUS = 15;
    protected static final int DEFAULT_CROSS_FADE_DURATION = 300;
    protected static final int DEFAULT_SCALE_DURATION = 300;
    protected static final int DEFAULT_ROTATION_DURATION = 300;

    protected Context context;
    protected String targetUrl;

    protected V targetView;
    protected boolean setImageAsBackground = false;
    protected int requiredImageWidth = -1;
    protected long maxCacheDurationMs = -1;

    protected List<ImageFilter<Bitmap>> bitmapImageFilters = new ArrayList<ImageFilter<Bitmap>>();

    protected boolean showStubOnExecute = true;
    protected boolean showStubOnError = false;
    protected boolean disableExecutionStubIfDownloaded = true;
    protected boolean useOldResourceStubs = false;
    protected StubHolder stubHolder;

    protected TransitionController transitionController = new TransitionController(this);
    protected boolean exitTransitionsEnabled = true;
    protected boolean transitionOnSuccessEnabled = true;

    protected Map<String, String> httpRequestParams = new HashMap<String, String>();

    protected ImageSuccessCallback successCallback;
    protected ImageErrorCallback errorCallback;

    protected boolean tagRequestPreventionEnabled = false;

    protected long startedAtMs;

    public ImageRequest(Context context) {
        this(context, "");
    }

    public ImageRequest(Context context, String targetUrl) {
        this.context = context;
        this.targetUrl = targetUrl;
        this.showStubOnExecute = context.getResources().getBoolean(R.bool.ail__show_stub_on_execute);
        this.showStubOnError = context.getResources().getBoolean(R.bool.ail__show_stub_on_error);
        this.useOldResourceStubs = context.getResources().getBoolean(R.bool.ail__use_old_resource_stubs);
    }

    public ImageRequest<V> setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
        return this;
    }

    public ImageRequest<V> setTargetView(V targetView) {
        this.targetView = targetView;
        return this;
    }

    /**
     * Add HttpUrlConnection Request Parameter key/value pair. Adding a key/value pair will override previous values for said key.
     */
    public ImageRequest<V> addHttpRequestParam(String key, String value) {
        httpRequestParams.put(key, value);
        return this;
    }

    /**
     * Force request to load image at specific size
     */
    public ImageRequest<V> setRequiredImageWidth(int requiredImageWidth) {
        this.requiredImageWidth = requiredImageWidth;
        return this;
    }

    /**
     * Force request to load image via setBackground
     */
    public ImageRequest<V> setImageAsBackground() {
        this.setImageAsBackground = true;
        return this;
    }

    public ImageRequest<V> setImageAsBackground(boolean setImageAsBackground) {
        this.setImageAsBackground = setImageAsBackground;
        return this;
    }

    public ImageRequest<V> setShowStubOnExecute(boolean showStubOnExecute) {
        this.showStubOnExecute = showStubOnExecute;
        return this;
    }

    public ImageRequest<V> setShowStubOnError(boolean showStubOnError) {
        this.showStubOnError = showStubOnError;
        return this;
    }

    public ImageRequest<V> setDisableExecutionStubIfDownloaded(boolean disableExecutionStubIfDownloaded) {
        this.disableExecutionStubIfDownloaded = disableExecutionStubIfDownloaded;
        return this;
    }

    /**
     * Use the old resource-based drawable stubs. Manually settings the stubs renders this useless.
     */
    public ImageRequest<V> setUseOldResourceStubs(boolean useOldResourceStubs) {
        this.useOldResourceStubs = useOldResourceStubs;
        return this;
    }

    /**
     * Set the maximum allowed time a cached file from this request is valid for in milliseconds.
     * Files requested beyond this limit will be deleted and re-downloaded.
     * Anything < 0 will never re-download (default behavior).
     */
    public ImageRequest<V> setMaxCacheDurationMs(long maxCacheDurationMs) {
        this.maxCacheDurationMs = maxCacheDurationMs;
        return this;
    }

    /**
     * Set a callback to be triggered on the main thread once the resulting image has been set
     * into the target View. This will not be triggered if the View is null.
     */
    public ImageRequest<V> setSuccessCallback(ImageSuccessCallback successCallback) {
        this.successCallback = successCallback;
        return this;
    }

    /**
     * Set a callback to be triggered in the event of a loading error. This will not
     * be triggered if the target View is null.
     */
    public ImageRequest<V> setErrorCallback(ImageErrorCallback errorCallback) {
        this.errorCallback = errorCallback;
        return this;
    }

    public ImageRequest<V> addBlurFilter(){
        return addBlurFilter(DEFAULT_BLUR_RADIUS);
    }

    public ImageRequest<V> addBlurFilter(int blurRadius){
        bitmapImageFilters.add(new BitmapBlurFilter(context, blurRadius));
        return this;
    }

    public ImageRequest<V> addColorOverlayFilter(int colorOverlay) {
        bitmapImageFilters.add(new BitmapColorOverlayFilter(context, colorOverlay));
        return this;
    }

    public ImageRequest<V> addRotationFilter(int rotationDegrees) {
        bitmapImageFilters.add(new BitmapRotationFilter(context, rotationDegrees));
        return this;
    }

    public ImageRequest<V> addCircularCropFilter() {
        bitmapImageFilters.add(new BitmapCircularCropFilter(context));
        return this;
    }

    /**
     * Replace all color values with the supplied color value while maintaining proper opacity at each pixel.
     * Intended for single color-scaled images.
     * @param replacementColor the 24-bit color value to replace with (0xAARRGGBB)
     */
    public ImageRequest<V> addColorOverrideFilter(int replacementColor){
        bitmapImageFilters.add(new BitmapColorOverrideFilter(context, replacementColor));
        return this;
    }

    public ImageRequest<V> addColorReplacementFilter(int replace, int with) {
        bitmapImageFilters.add(new BitmapColorReplacementFilter(context, replace, with));
        return this;
    }

    public ImageRequest<V> addColorReplacementFilter(Map<Integer, Integer> replacements) {
        bitmapImageFilters.add(new BitmapColorReplacementFilter(context, replacements));
        return this;
    }

    public ImageRequest<V> addColorFilter(ColorFilter filter) {
        bitmapImageFilters.add(new BitmapColorFilter(context, filter));
        return this;
    }

    public ImageRequest<V> addCenterCropFilter(int width, int height) {
        bitmapImageFilters.add(new BitmapCenterCropFilter(context, new int[]{ width, height }));
        return this;
    }

    public ImageRequest<V> addImageFilter(ImageFilter<Bitmap> imageFilter){
        bitmapImageFilters.add(imageFilter);
        return this;
    }

    public ImageRequest<V> overrideStubs(StubHolder stubHolder){
        this.stubHolder = stubHolder;
        return this;
    }

    public ImageRequest<V> setDefaultImageTransition(){
        transitionController.unregisterModules();
        return this;
    }

    public ImageRequest<V> setFadeTransition(){
        return setFadeTransition(DEFAULT_CROSS_FADE_DURATION);
    }

    public ImageRequest<V> setFadeTransition(long duration){
        return addImageTransitionModule(new FadingTransitionModule(duration));
    }

    public ImageRequest<V> setScaleTransition(float from, float to){
        return setScaleTransition(from, to, DEFAULT_SCALE_DURATION);
    }

    public ImageRequest<V> setScaleTransition(float from, float to, long duration){
        return addImageTransitionModule(new ScalingTransitionModule(from, to, duration));
    }

    public ImageRequest<V> setRotationTransition(int from, int to){
        return setRotationTransition(from, to, DEFAULT_ROTATION_DURATION);
    }

    public ImageRequest<V> setRotationTransition(int from, int to, long duration){
        return addImageTransitionModule(new RotationTransitionModule(from, to, duration));
    }

    public ImageRequest<V> addImageTransitionModule(TransitionModule module){
        this.transitionController.registerModule(module);
        return this;
    }

    public ImageRequest<V> setExitTransitionsEnabled(boolean exitTransitionsEnabled){
        this.exitTransitionsEnabled = exitTransitionsEnabled;
        return this;
    }

    /**
     * Set whether or not the loader should automatically transition the View to the request's completed image
     * for cases where you want to handle the returned Bitmap before it's actually placed within the View.
     * See setSuccessCallback(ImageSuccessCallback) for more info
     */
    public ImageRequest<V> setTransitionOnSuccessEnabled(boolean transitionOnSuccessEnabled){
        this.transitionOnSuccessEnabled = transitionOnSuccessEnabled;
        return this;
    }

    /**
     * Override an Interpolator for a previously added TransitionModule
     * @param c the class of the module (e.g. FadingTransitionModule)
     * @param interpolatorId the int ID from TransitionModule keys or custom entries (e.g. TransitionModule.INTERPOLATOR_IN)
     * @param interpolator the interpolator to substitute with
     */
    public ImageRequest<V> registerImageTransitionInterpolator(Class c, int interpolatorId, Interpolator interpolator){
        this.transitionController.registerModuleInterpolator(c, interpolatorId, interpolator);
        return this;
    }

    /**
     * Enabling this will prevent subsequent requests for the same URL from running when the target is already set correctly.
     * This uses the target View's tag holder. Don't use this with other requests that don't have this flag enabled.
     */
    public ImageRequest<V> setTagRequestPreventionEnabled(boolean tagRequestPreventionEnabled){
        this.tagRequestPreventionEnabled = tagRequestPreventionEnabled;
        return this;
    }

    @Override
    public void run() {
        if(targetView != null && ImageLoader.getInstance(context).isViewStillUsable(this))
            performFullImageRequest();
    }

    protected void performFullImageRequest() {
        int requiredImageWidth = getRequiredImageWidth();

        File imageFile = getEditedRequestFile();
        if(!imageFile.exists()){
            File originalImageFile = getOriginalRequestFile();
            if(!originalImageFile.exists())
                onRequestFailed();
            else processImage(imageFile, ImageUtils.decodeFile(originalImageFile, requiredImageWidth));
        }
        else
            onRequestCompleted(ImageUtils.decodeFile(imageFile, requiredImageWidth));
    }

    protected int getRequiredImageWidth(){
        return requiredImageWidth < 1
                ? targetView.getLayoutParams().width
                : requiredImageWidth;
    }

    protected void processImage(File imageFile, Bitmap bitmap) {
        if(0 < bitmapImageFilters.size() && bitmap != null){
            try{
                bitmap = applyBitmapFilters(bitmap);

                saveBitmap(imageFile, bitmap);
            }
            catch(Throwable e){
                ImageUtils.log(context, e);

                // Let it fail gracefully if our filters couldn't recover
                bitmap = null;
            }
        }

        onRequestCompleted(bitmap);
    }

    protected Bitmap applyBitmapFilters(Bitmap bitmap){
        for(ImageFilter<Bitmap> filter : bitmapImageFilters)
            bitmap = filter.filter(bitmap);

        return bitmap;
    }

    protected void saveBitmap(File imageFile, Bitmap bitmap){
        ImageUtils.saveBitmapAsync(context, imageFile, bitmap);
    }

    protected void onRequestCompleted(@Nullable final Bitmap bitmap) {
        if(bitmap == null){
            onRequestFailed();
            return;
        }
        else if(targetView == null || !ImageLoader.getInstance(context).isViewStillUsable(this))
            return;

        if(transitionOnSuccessEnabled){
            BitmapDrawable targetDrawable = new BitmapDrawable(targetView.getContext().getResources(), bitmap);
            transitionController.transitionTo(targetDrawable);
        }

        targetView.post(new Runnable(){
            public void run(){
                if(successCallback != null)
                    successCallback.onImageReady(ImageRequest.this, bitmap);
            }
        });
    }

    protected void onRequestFailed() {
        if(targetView == null || !ImageLoader.getInstance(context).isViewStillUsable(this))
            return;

        handleShowStubOnError();

        targetView.post(new Runnable(){
            public void run(){
                if(errorCallback != null)
                    errorCallback.onImageLoadingFailure(ImageRequest.this,
                            new RuntimeException("Image could not be loaded"));
            }
        });
    }

    protected void handleShowStubOnError(){
        final Drawable targetDrawable = showStubOnError
                ? getStubs().getErrorDrawable(context)
                : ContextCompat.getDrawable(context, R.drawable.ail__default_fade_placeholder);

        transitionController.transitionTo(targetDrawable);
    }

    protected String getFullRequestFileCacheName() {
        String adjustedName = targetUrl;

        for(ImageFilter<Bitmap> filter : bitmapImageFilters)
            adjustedName += "_" + filter.getAdjustmentInfo();

        return adjustedName;
    }

    protected File getEditedRequestFile() {
        return ImageLoader.getInstance(context).getFileCache().getFile(getFullRequestFileCacheName());
    }

    protected String getOriginalRequestFileCacheName() {
        return targetUrl;
    }

    public Context getContext(){
        return context;
    }

    public boolean isSetImageAsBackgroundForced(){
        return setImageAsBackground;
    }

    public boolean isRequestForBackgroundImage(){
        return setImageAsBackground || !(targetView instanceof ImageView);
    }

    public V getTargetView(){
        return targetView;
    }

    public Map<String, String> getHttpRequestParams(){
        return httpRequestParams;
    }

    public long getMaxCacheDurationMs(){
        return maxCacheDurationMs;
    }

    /**
     * @return the File associated with the target URL. File won't be null, but may not exist.
     */
    public File getOriginalRequestFile() {
        return ImageLoader.getInstance(context)
                .getFileCache()
                .getFile(getOriginalRequestFileCacheName());
    }

    protected StubHolder getStubs(){
        if(stubHolder == null)
            return useOldResourceStubs
                    ? ImageLoader.getInstance(context).getResourceBasedStubs()
                    : ImageLoader.getInstance(context).getStubs();
        else return stubHolder;
    }

    public String getTargetUrl(){
        return targetUrl;
    }

    public boolean isExitTransitionEnabled(){
        return exitTransitionsEnabled;
    }

    public long getStartedAtMs(){
        return startedAtMs;
    }

    private void handleShowStubOnExecute(){
        if(disableExecutionStubIfDownloaded
                && ImageLoader.getInstance(context).isImageDownloaded(this)
                && ImageLoader.getInstance(context).getFileCache().isCachedFileValid(targetUrl, maxCacheDurationMs))
            return;

        final Drawable targetDrawable = showStubOnExecute
                ? getStubs().getLoadingDrawable(context)
                : ContextCompat.getDrawable(context, R.drawable.ail__default_fade_placeholder);

        transitionController.transitionTo(targetDrawable);
    }

    public ImageRequest<V> execute() {
        final String fullRequestFile = getFullRequestFileCacheName();

        if(tagRequestPreventionEnabled
                && !(targetView == null || targetView.getTag() == null)
                && targetView.getTag().equals(fullRequestFile)){
            ImageUtils.log(context, "Request already claimed. Ignoring [" + fullRequestFile + "]");

            return this;
        }

        startedAtMs = System.currentTimeMillis();

        if(targetView == null)
            ImageLoader.getInstance(context)
                    .submit(this);
        else
            ImageLoader.getInstance(context)
                    .getHandler()
                    .post(new Runnable(){
                        public void run(){
                            if(tagRequestPreventionEnabled)
                                targetView.setTag(fullRequestFile);

                            ImageLoader.getInstance(context)
                                    .claimViewTarget(ImageRequest.this);

                            handleShowStubOnExecute();

                            targetView.post(new Runnable() {
                                public void run() {
                                    ImageLoader.getInstance(context)
                                            .submit(ImageRequest.this);
                                }
                            });
                        }
                    });

        return this;
    }

    /**
     * @param delay amount to delay the execution by
     */
    public ImageRequest<V> execute(long delay){
        ImageLoader.getInstance(context)
                .getHandler()
                .postDelayed(new Runnable(){
                    public void run(){
                        execute();
                    }
                }, delay);

        return this;
    }

}
