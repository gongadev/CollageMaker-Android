package com.azoft.azoft.collage.loaders;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;

import com.azoft.azoft.collage.R;
import com.azoft.azoft.collage.data.CollageFillData;
import com.azoft.azoft.collage.data.CollageRegionData;
import com.azoft.azoft.collage.exceptions.CollageCreationException;
import com.azoft.azoft.collage.utils.CollageRegion;
import com.azoft.azoft.collage.utils.MediaUtils;
import com.mig35.loaderlib.loaders.DataAsyncTaskLibLoader;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Construct result image. And store it to internal directory and return its path in FileProvider.
 * <p/>
 * Date: 4/9/2014
 * Time: 6:19 PM
 *
 * @author MiG35
 */
public class CollagePreviewCreatorLoader extends DataAsyncTaskLibLoader<String> {

    private final CollageFillData mCollageFillData;

    public CollagePreviewCreatorLoader(final Context context, final CollageFillData collageFillData) {
        super(context);
        if (null == collageFillData) {
            throw new IllegalArgumentException("collageFillData can't be null");
        }

        mCollageFillData = collageFillData;
    }

    @Override
    protected String performLoad() throws Exception {
        final Map<CollageRegion, CollageRegionData> collageDataMap = new HashMap<>();

        int maxSize = 0; // we need to know our result image size (as biggest image size).
        for (final CollageRegion collageRegion : mCollageFillData.getCollageRegions()) {
            final CollageRegionData regionData = mCollageFillData.getRegionData(collageRegion);
            maxSize = Math.max(maxSize, getImageSize(regionData.getImageFile()));
            collageDataMap.put(collageRegion, regionData);
        }

        int sampleSize = 1;
        Bitmap outBitmap = null;
        do {
            try {
                outBitmap = Bitmap.createBitmap(maxSize / sampleSize, maxSize / sampleSize, Bitmap.Config.ARGB_8888);

                final Canvas canvas = new Canvas(outBitmap);
                canvas.drawColor(getContext().getResources().getColor(R.color.collage_bg_color));

                for (final Map.Entry<CollageRegion, CollageRegionData> entryItem : collageDataMap.entrySet()) {
                    drawCollageRegionOnCanvas(canvas, entryItem.getKey(), entryItem.getValue());
                }
                break;
            } catch (final OutOfMemoryError thr) {
                sampleSize *= 2;
                if (null != outBitmap) {
                    outBitmap.recycle();
                }
                outBitmap = null;
            }
        } while (sampleSize < 100);

        if (null == outBitmap) {
            throw new CollageCreationException(new OutOfMemoryError());
        }
        try {
            return MediaUtils.insertImage(outBitmap, getContext().getString(R.string.text_image_collage_preview));
        } finally {
            outBitmap.recycle();
        }
    }

    private void drawCollageRegionOnCanvas(final Canvas canvas, final CollageRegion collageRegion, final CollageRegionData collageRegionData) throws CollageCreationException {
        for (int sampleSize = 1; sampleSize < 100; sampleSize *= 2) {
            try {
                drawCollageRegionOnCanvasOld(canvas, collageRegion, collageRegionData, sampleSize);
                return;
            } catch (final OutOfMemoryError error) {
                // pass
            }
        }
        throw new OutOfMemoryError();
    }

    private void drawCollageRegionOnCanvasOld(final Canvas canvas, final CollageRegion collageRegion, final CollageRegionData collageRegionData, final int sampleSize)
            throws CollageCreationException {
        // region visible width and height
        final int regionWidth = (int) (canvas.getWidth() * collageRegion.getWidth());
        final int regionHeight = (int) (canvas.getWidth() * collageRegion.getHeight());

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        final Bitmap regionDecodedBitmap = BitmapFactory.decodeFile(collageRegionData.getImageFile().getAbsolutePath(), options);
        if (null == regionDecodedBitmap) {
            throw new CollageCreationException();
        }
        final Bitmap memoryOptimizedDecodedBitmap;
        if (regionDecodedBitmap.getWidth() == regionWidth && regionDecodedBitmap.getHeight() == regionHeight) {
            // decoded bitmap is the same as our region. nothing to do more
            memoryOptimizedDecodedBitmap = regionDecodedBitmap;
        } else {
            // we should make our decoded bitmap scaled for region. because region may be not square we use it's max size
            final float imageScale = Math.max(1f * regionWidth / regionDecodedBitmap.getWidth(), 1f * regionHeight / regionDecodedBitmap.getHeight());
            final Bitmap tmp = Bitmap.createScaledBitmap(regionDecodedBitmap, Math.round(imageScale * regionDecodedBitmap.getWidth()), Math
                    .round(imageScale * regionDecodedBitmap.getHeight()), true);
            if (tmp != regionDecodedBitmap) {
                regionDecodedBitmap.recycle();
            }
            if (null == tmp) {
                memoryOptimizedDecodedBitmap = null;
            } else {
                memoryOptimizedDecodedBitmap = tmp;
            }
        }
        if (null == memoryOptimizedDecodedBitmap) {
            throw new CollageCreationException();
        }
        final float scale = collageRegionData.getImageScale();
        final float left = collageRegionData.getImageLeft() == null ? 0 : collageRegionData.getImageLeft();
        final float top = collageRegionData.getImageTop() == null ? 0 : collageRegionData.getImageTop();

        // we should crop image to be exactly as our scaled region (then we should scale it upper)
        final Bitmap scaledResultBitmap =
                Bitmap.createBitmap(memoryOptimizedDecodedBitmap, (int) (left * (memoryOptimizedDecodedBitmap.getWidth() - regionWidth / scale)),
                        (int) (top * (memoryOptimizedDecodedBitmap.getHeight() - regionHeight / scale)), (int) (regionWidth / scale),
                        (int) (regionHeight / scale));
        if (scaledResultBitmap != memoryOptimizedDecodedBitmap) {
            memoryOptimizedDecodedBitmap.recycle();
        }
        final Bitmap resultBitmap = Bitmap.createScaledBitmap(scaledResultBitmap, regionWidth, regionHeight, true);
        if (resultBitmap != scaledResultBitmap) {
            scaledResultBitmap.recycle();
        }

        canvas.drawBitmap(resultBitmap, (int) (collageRegion.getLeft() * canvas.getWidth()), (int) (collageRegion.getTop() * canvas.getHeight()),
                null);
        resultBitmap.recycle();
    }

    private int getImageSize(final File imageFile) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
        return Math.max(options.outWidth, options.outHeight);
    }
}
