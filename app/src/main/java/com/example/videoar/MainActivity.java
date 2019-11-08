package com.example.videoar;


import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.Frame;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.ExternalTexture;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
public class MainActivity extends AppCompatActivity {

    private ArFragment arFragment;
    private ImageView fitToScanView;
    private FloatingActionButton floatingActionButton;

    // Augmented image and its associated center pose anchor, keyed by the augmented image in
    // the database.
    //private final Map<AugmentedImage, AugmentedImageNode> augmentedImageMap = new HashMap<>();
    private ArrayList<AugmentedImage> augmentedImageArrayList;
    //Video Staff
    private MediaPlayer mediaPlayer;
    private ExternalTexture texture;
    private ModelRenderable videoRenderable;
    // The color to filter out of the video.
    private static final Color CHROMA_KEY_COLOR = new Color(0.1843f, 1.0f, 0.098f);

    // Controls the height of the video in world space.
    private static final float VIDEO_HEIGHT_METERS = 0.85f;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        //val hsa: Snackbar
        fitToScanView = findViewById(R.id.image_view_fit_to_scan);

        arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
            try {
                onUpdateFrame(frameTime);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        floatingActionButton = findViewById(R.id.floatingActionButton);
        augmentedImageArrayList = new ArrayList<AugmentedImage>();

        initialize();

        showFab(false);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.e("onClick", "button to show video");


            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        //if (augmentedImageMap.isEmpty()) {
        if (augmentedImageArrayList.isEmpty()) {
            fitToScanView.setVisibility(View.VISIBLE);
        }
    }

    // Simple function to show/hide our FAB
    @SuppressLint("RestrictedApi")
    private void showFab(Boolean enabled) {
        if (enabled) {
            floatingActionButton.setEnabled(true);
            floatingActionButton.setVisibility(View.VISIBLE);
        } else {
            floatingActionButton.setEnabled(false);
            floatingActionButton.setVisibility(View.GONE);
        }
    }

    Node videoNode = new Node();

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void initialize(){
        texture = new ExternalTexture();

        // Create an Android MediaPlayer to capture the video on the external texture's surface.
        mediaPlayer = MediaPlayer.create(this, R.raw.simon);
        mediaPlayer.setLooping(true);

        ModelRenderable.builder()
                .setSource(this, Uri.parse("chroma_key_video.sfb"))
                .build()
                .thenAccept(
                        renderable -> {
                            mediaPlayer.setSurface(texture.getSurface());
                            renderable.getMaterial().setExternalTexture("videoTexture", texture);
                            renderable.setShadowCaster(false);
                            renderable.setShadowReceiver(false);
                            videoRenderable = renderable;

                        })
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this, "Unable to load video renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });
    }

    private void settingVideoRenderable(AugmentedImage augmentedImage) throws IOException {

        Anchor anchor = augmentedImage.createAnchor(augmentedImage.getCenterPose());
        AnchorNode anchorNode = new AnchorNode(anchor);
        videoNode.setParent(anchorNode);
        //videoNode.setLocalPosition(new Vector3(augmentedImage.getExtentX(), 1.0f,augmentedImage.getExtentZ()));
        Log.e("videoNode", videoNode.toString());
        // Set the scale of the node so that the aspect ratio of the video is correct.
        float videoWidth = mediaPlayer.getVideoWidth();
        float videoHeight = mediaPlayer.getVideoHeight();
        /*videoNode.setLocalScale(
                new Vector3(
                        VIDEO_HEIGHT_METERS * (videoWidth / videoHeight), VIDEO_HEIGHT_METERS, 1.0f));*/
        videoNode.setLocalScale(new Vector3(augmentedImage.getExtentX(), 1.0f,augmentedImage.getExtentZ()));
        // Start playing the video when the first node is placed.
        if (!mediaPlayer.isPlaying()) {
            Log.e("mediaPlayer", String.valueOf(mediaPlayer.isPlaying()));
            // Wait to set the renderable until the first frame of the  video becomes available.
            // This prevents the renderable from briefly appearing as a black quad before the video
            // plays.
            mediaPlayer.prepare();
            mediaPlayer.start();


            texture
                    .getSurfaceTexture()
                    .setOnFrameAvailableListener(
                      (SurfaceTexture surfaceTexture) -> {
                          texture.getSurfaceTexture().setOnFrameAvailableListener(null);
                          videoNode.setRenderable(videoRenderable);
                      });
        }
        arFragment.getArSceneView().getScene().addChild(videoNode);
    }
    /**
     * Registered with the Sceneform Scene object, this method is called at the start of each frame.
     *
     * @param frameTime - time since last frame.
     */
    private void onUpdateFrame(FrameTime frameTime) throws IOException {
        Frame frame = arFragment.getArSceneView().getArFrame();

        // If there is no frame or ARCore is not tracking yet, just return.
        if (frame == null || frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
            return;
        }

        Collection<AugmentedImage> updatedAugmentedImages =
                frame.getUpdatedTrackables(AugmentedImage.class);
        for (AugmentedImage augmentedImage : updatedAugmentedImages) {
            switch (augmentedImage.getTrackingState()) {
                case PAUSED:
                    // When an image is in PAUSED state, but the camera is not PAUSED, it has been detected,
                    // but not yet tracked.
                    String text = "Detected Image " + augmentedImage.getIndex();
                    Log.e("TRACKING", text);
                    break;

                case TRACKING:
                    // Have to switch to UI Thread to update View.
                    fitToScanView.setVisibility(View.GONE);



                    // Create a new anchor for newly found images.
                    if (!augmentedImageArrayList.contains(augmentedImage)) {
                        //SHOW BUTTON TO TRIGGER VIDEO
                        showFab(true);

                        augmentedImageArrayList.add(augmentedImage);
                        String msg = "Click the button!! ";
                        Log.e("onUpdateFrame", msg);
                        settingVideoRenderable(augmentedImage);

                    }

                    break;

                case STOPPED:
                    //augmentedImageMap.remove(augmentedImage);
                    augmentedImageArrayList.remove(augmentedImage);
                    break;
            }
        }
    }
}
