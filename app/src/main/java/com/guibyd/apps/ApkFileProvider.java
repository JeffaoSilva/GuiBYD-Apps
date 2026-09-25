package com.guibyd.apps;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public class ApkFileProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..")) {
            throw new FileNotFoundException("Invalid file");
        }

        File cache = new File(getContext().getCacheDir(), "installer");
        File file = new File(cache, name);

        try {
            String base = cache.getCanonicalPath();
            String target = file.getCanonicalPath();
            if (!target.startsWith(base + File.separator)) {
                throw new FileNotFoundException("Invalid path");
            }
        } catch (Exception e) {
            throw new FileNotFoundException("Invalid path");
        }

        if (!file.exists()) {
            throw new FileNotFoundException(name);
        }
        return file;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        return ParcelFileDescriptor.open(
                resolve(uri),
                ParcelFileDescriptor.MODE_READ_ONLY
        );
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {
        try {
            File file = resolve(uri);
            MatrixCursor cursor = new MatrixCursor(
                    new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
            );
            cursor.addRow(new Object[]{file.getName(), file.length()});
            return cursor;
        } catch (Exception e) {
            return null;
        }
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
