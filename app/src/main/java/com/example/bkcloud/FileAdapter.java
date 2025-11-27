package com.example.bkcloud;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    private List<FileItem> files;

    public static class FileItem {
        public String name;
        public long size;

        public FileItem(String name, long size) {
            this.name = name;
            this.size = size;
        }
    }

    public FileAdapter(List<FileItem> files) {
        this.files = files;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtFileName, txtFileSize;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtFileName = itemView.findViewById(R.id.txtFileName);
            txtFileSize = itemView.findViewById(R.id.txtFileSize);
        }
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
        FileItem file = files.get(position);
        holder.txtFileName.setText(file.name);
        holder.txtFileSize.setText(formatSize(file.size));
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

}
