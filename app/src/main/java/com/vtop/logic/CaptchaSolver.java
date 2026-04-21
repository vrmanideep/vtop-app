package com.vtop.logic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;

public class CaptchaSolver {

    private double[][] weights;
    private double[] biases;
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private boolean isLoaded = false;

    // 1. Initialize and load the brain from the JSON file
    // 1. Initialize and load the brain from the JSON file
    public CaptchaSolver(Context context) {
        try {
            java.io.InputStream is = context.getAssets().open("weights.json");

            // Foolproof way to read the entire file into a String
            java.util.Scanner scanner = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
            String jsonString = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
            is.close();

            if (jsonString.isEmpty()) {
                android.util.Log.e("SOLVER", "weights.json is empty or not found!");
                return;
            }

            JSONObject brain = new JSONObject(jsonString);
            JSONArray weightsArray = brain.getJSONArray("weights");
            JSONArray biasesArray = brain.getJSONArray("biases");

            int rows = weightsArray.length();
            int cols = weightsArray.getJSONArray(0).length();

            weights = new double[rows][cols];
            for (int i = 0; i < rows; i++) {
                JSONArray row = weightsArray.getJSONArray(i);
                for (int j = 0; j < cols; j++) {
                    weights[i][j] = row.getDouble(j);
                }
            }

            biases = new double[biasesArray.length()];
            for (int i = 0; i < biasesArray.length(); i++) {
                biases[i] = biasesArray.getDouble(i);
            }
            isLoaded = true;

        } catch (Exception e) {
            android.util.Log.e("SOLVER", "Error loading weights", e);
        }
    }

    // 2. The Main Solver logic translated from your Rust script
    public String solve(Bitmap bitmap) {
        if (!isLoaded || bitmap == null) return null;

        // Ensure Bitmap matches the VTOP 200x40 dimension
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 200, 40, false);

        // A. Build the Saturation Matrix
        float[][] satImg = new float[40][200];
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 200; x++) {
                int pixel = scaledBitmap.getPixel(x, y);
                float r = Color.red(pixel);
                float g = Color.green(pixel);
                float b = Color.blue(pixel);

                float max = Math.max(r, Math.max(g, b));
                float min = Math.min(r, Math.min(g, b));
                satImg[y][x] = (max > 0) ? ((max - min) * 255f / max) : 0;
            }
        }

        StringBuilder out = new StringBuilder();

        // B. Slice into 6 characters and run the ML math
        for (int i = 0; i < 6; i++) {
            int x1 = (i + 1) * 25 + 2;
            int y1 = 7 + 5 * (i % 2) + 1;
            int x2 = (i + 2) * 25 + 1;
            int y2 = 35 - 5 * ((i + 1) % 2);

            int width = x2 - x1;   // Should be 24
            int height = y2 - y1;  // Should be 22

            // Calculate local average for thresholding
            float avg = 0;
            for (int r = y1; r < y2; r++) {
                for (int c = x1; c < x2; c++) {
                    avg += satImg[r][c];
                }
            }
            avg /= (width * height);

            // Flatten into a 1D binary array (Size: 528)
            int[] flattened = new int[width * height];
            int idx = 0;
            for (int r = y1; r < y2; r++) {
                for (int c = x1; c < x2; c++) {
                    flattened[idx++] = (satImg[r][c] > avg) ? 1 : 0;
                }
            }

            // C. Matrix Dot Product (x * weights + biases)
            double max_val = Double.NEGATIVE_INFINITY;
            int max_idx = 0;

            for (int j = 0; j < biases.length; j++) {
                double sum = biases[j];
                for (int k = 0; k < flattened.length; k++) {
                    sum += flattened[k] * weights[k][j];
                }

                // We don't even need the Softmax exponential here!
                // Since exp(x) is monotonic, the highest raw sum will ALWAYS be the highest probability.
                if (sum > max_val) {
                    max_val = sum;
                    max_idx = j;
                }
            }
            out.append(LETTERS.charAt(max_idx));
        }

        return out.toString();
    }
}