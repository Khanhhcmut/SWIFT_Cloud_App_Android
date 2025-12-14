package com.example.bkcloud;

import android.graphics.Color;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    private List<FileItem> files;
    List<FileItem> originalList;
    boolean deleteMode = false;
    Set<String> selectedSet = new HashSet<>();
    FileListener listener;


    public static class FileItem {
        public String name;
        public long size;
        public String folder;
        public String last;

        public FileItem(String name, long size, String folder, String last) {
            this.name = name;
            this.size = size;
            this.folder = folder;
            this.last = last;
        }

    }

    public FileAdapter(List<FileItem> files) {
        this.files = files;
        this.originalList = new ArrayList<>(files);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtFileName, txtFileSize;
        ImageView deleteIcon;
        ImageView checkIcon;
        TextView txtIndex;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtFileName = itemView.findViewById(R.id.txtFileName);
            txtFileSize = itemView.findViewById(R.id.txtFileSize);
            deleteIcon = itemView.findViewById(R.id.deleteIcon);
            checkIcon = itemView.findViewById(R.id.checkIcon);
            txtIndex = itemView.findViewById(R.id.txtIndex);
        }
    }

    public interface FileListener {
        void onLongPress(String fileName);
        void onToggleSelect(String fileName);
        void onClickDeleteIcon();
    }

    @NonNull
    @Override
    public FileAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull FileAdapter.ViewHolder holder, int position) {

        holder.txtIndex.setText(String.valueOf(position + 1));
        FileItem file = files.get(position);
        holder.txtFileName.setText(file.name);
        String key = file.folder + "/" + file.name;

        holder.itemView.setOnTouchListener(new View.OnTouchListener() {
            Handler handler = new Handler();
            boolean isLong = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    isLong = false;
                    handler.postDelayed(() -> {
                        isLong = true;
                        if (listener != null) listener.onLongPress(key);
                    }, 1000);
                }

                if (event.getAction() == MotionEvent.ACTION_UP ||
                        event.getAction() == MotionEvent.ACTION_CANCEL) {

                    handler.removeCallbacksAndMessages(null);
                }

                return false;
            }
        });

        holder.itemView.setOnClickListener(v -> {});

        holder.deleteIcon.setOnClickListener(v -> {
            if (listener != null) listener.onClickDeleteIcon();
        });

        holder.txtFileSize.setText(
                formatSize(file.size) + " • " + formatLastModified(file.last)
        );

        if (deleteMode) {
            holder.deleteIcon.setVisibility(View.VISIBLE);
            holder.checkIcon.setVisibility(View.VISIBLE);

            holder.checkIcon.setOnClickListener(v -> {
                if (listener != null) listener.onToggleSelect(key);
            });

            if (selectedSet.contains(key)) {
                holder.itemView.setBackgroundColor(Color.parseColor("#33FF0000"));
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            }

        } else {
            holder.checkIcon.setVisibility(View.GONE);
            holder.deleteIcon.setVisibility(View.GONE);
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }

    }

    public void setDeleteMode(boolean mode, Set<String> set) {
        deleteMode = mode;
        selectedSet = new HashSet<>(set);
        notifyDataSetChanged();
    }

    public void setListener(FileListener l) {
        this.listener = l;
    }


    @Override
    public int getItemCount() {
        return files.size();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), unit);
    }

    public void filter(String key) {
        files.clear();

        if (key.isEmpty()) {
            files.addAll(originalList);
        } else {
            for (FileItem f : originalList) {
                String nameNorm = stripAccent(f.name).toLowerCase();
                if (nameNorm.contains(key)) {
                    files.add(f);
                }
            }
        }

        notifyDataSetChanged();
    }

    private String stripAccent(String s) {
        if (s == null) return "";
        String temp = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return temp.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private String formatLastModified(String raw) {
        try {
            // Swift format: 2024-05-03T14:22:31.123456
            String clean = raw.split("\\.")[0]; // bỏ phần .123456
            SimpleDateFormat src = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            Date d = src.parse(clean);

            SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US);
            return out.format(d);

        } catch (Exception e) {
            return raw;
        }
    }


}
