package com.example.litebrowser

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import coil.load
import com.example.litebrowser.databinding.FragmentImageViewerBinding

class ImageViewerFragment : DialogFragment() {

    private var _binding: FragmentImageViewerBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(imageUrl: String): ImageViewerFragment = ImageViewerFragment().apply {
            arguments = Bundle().apply { putString("imageUrl", imageUrl) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentImageViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.black)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val url = requireArguments().getString("imageUrl") ?: return

        binding.progressBar.isVisible = true
        binding.photoView.maximumScale = 8f
        binding.photoView.minimumScale = 0.8f

        binding.photoView.load(url) {
            crossfade(true)
            listener(
                onSuccess = { _, _ -> binding.progressBar.isVisible = false },
                onError = { _, _ ->
                    binding.progressBar.isVisible = false
                    Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            )
        }

        binding.photoView.setOnScaleChangeListener { scaleFactor, _, _ ->
            if (scaleFactor < 0.85f) dismiss()
        }

        binding.btnBack.setOnClickListener { dismiss() }
        binding.btnDownload.setOnClickListener {
            (activity as? MainActivity)?.downloadImageWithPermission(url)
        }
        binding.btnShare.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            startActivity(Intent.createChooser(intent, "Share image"))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
