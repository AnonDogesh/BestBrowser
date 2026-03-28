package com.example.litebrowser

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.litebrowser.databinding.FragmentImageActionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import coil.load

class ImageActionSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentImageActionBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(imageUrl: String): ImageActionSheet = ImageActionSheet().apply {
            arguments = Bundle().apply { putString("imageUrl", imageUrl) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentImageActionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val url = requireArguments().getString("imageUrl") ?: return

        binding.ivPreview.load(url) {
            crossfade(true)
            placeholder(R.drawable.ic_image_placeholder)
            error(R.drawable.ic_broken_image)
        }

        binding.btnNewTab.setOnClickListener {
            (activity as? BrowserCallback)?.openInNewTab(url)
            dismiss()
        }

        binding.btnImageViewer.setOnClickListener {
            ImageViewerFragment.newInstance(url).show(parentFragmentManager, "viewer")
            dismiss()
        }

        binding.btnDownload.setOnClickListener {
            (activity as? MainActivity)?.downloadImageWithPermission(url)
            dismiss()
        }

        binding.btnShare.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            startActivity(Intent.createChooser(intent, "Share image"))
            dismiss()
        }

        binding.btnClose.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
