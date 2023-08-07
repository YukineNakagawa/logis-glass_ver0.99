package co.jp.koyu.arpicking;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;


/**
 * An image view specifically to draw the barcode to our screen
 */
public class ScanResultImageView extends ImageView {

    private Point[] location;
    private Paint locationPaint;

    public ScanResultImageView(Context context) {
        this(context, null);
    }

    public ScanResultImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * The constructor taking all available parameters. Called directly, or by other constructors
     *
     * @param context      The Context in which we are operating
     * @param attrs        - The AttributeSet or null
     * @param defStyleAttr The int style, or zero
     */
    public ScanResultImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setAdjustViewBounds(true);

        // Setup a Paint object to draw on the scanned barcode with these attributes
        locationPaint = new Paint();
        locationPaint.setColor(Color.GREEN);
        locationPaint.setStrokeWidth(5);
        locationPaint.setStyle(Paint.Style.STROKE);
        locationPaint.setStrokeCap(Paint.Cap.ROUND);
        locationPaint.setAntiAlias(true);
    }

    /**
     * Accessor to get the location of the barcode within the image
     *
     * @return Location of barcode
     */
    public Point[] getLocation() {
        return location;
    }

    /**
     * Mutator to set the location of the barcode within the image, and re-draw accordingly
     *
     * @param location Location object defining the recognized barcode within the image
     */
    public void setLocation(Point[] location) {
        this.location = location;
        invalidate();
    }

    /**
     * Override this to draw a box around the barcode at the location it was found
     *
     * @param canvas Canvas upon which to draw
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (location != null) {
            Drawable d = getDrawable();
            if (d instanceof BitmapDrawable) {
                Bitmap b = ((BitmapDrawable) d).getBitmap();
                float scale = (float) getWidth() / b.getWidth();
                if (location != null && location.length > 1) {
                    for (int iIndex = 1; iIndex < location.length; iIndex++) {
                        drawLine(canvas, location[iIndex - 1], location[iIndex], scale);
                    }
                    drawLine(canvas, location[location.length - 1], location[0], scale);
                }
            }
        }
    }

    /**
     * Utility to draw a single line onto the canvas
     *
     * @param canvas Canvas upon which to draw
     * @param p1     Point to start the line
     * @param p2     Point to end the line
     * @param scale  float by which to scale the line. Converts from high-res bitmap dimensions to Canvas dimensions
     */
    private void drawLine(Canvas canvas, Point p1, Point p2, float scale) {
        canvas.drawLine(p1.x * scale, p1.y * scale, p2.x * scale, p2.y * scale, locationPaint);
    }
}
