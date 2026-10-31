package com.example.uepflip_flashcard.ui.main

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.example.uepflip_flashcard.R

/** One onboarding page. Holds resource ids so the copy stays translatable. */
data class Page(val image: Int, val title: Int, val body: Int)

class OnboardingFragment : Fragment(R.layout.activity_onboarding) {

    private val pages = listOf(
        Page(R.drawable.ic_mascot, R.string.onboarding_page_1_title, R.string.onboarding_page_1_body),
        Page(R.drawable.ill_study, R.string.onboarding_page_2_title, R.string.onboarding_page_2_body),
        Page(R.drawable.ill_create, R.string.onboarding_page_3_title, R.string.onboarding_page_3_body),
        Page(R.drawable.ill_progress, R.string.onboarding_page_4_title, R.string.onboarding_page_4_body)
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val main = requireActivity() as MainActivity
        val pager = view.findViewById<ViewPager2>(R.id.pager)
        val next = view.findViewById<Button>(R.id.btn_next)
        val skip = view.findViewById<TextView>(R.id.btn_skip)

        pager.adapter = SimplePagerAdapter(pages)

        TabLayoutMediator(
            view.findViewById<com.google.android.material.tabs.TabLayout>(R.id.dots),
            pager
        ) { _, _ -> }.attach()

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                next.setText(
                    if (position == pages.lastIndex) R.string.onboarding_start else R.string.onboarding_next
                )
                skip.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
            }
        })

        next.setOnClickListener {
            if (pager.currentItem < pages.lastIndex) {
                pager.currentItem += 1
            } else {
                main.push(SetupFeaturesFragment())
            }
        }

        skip.setOnClickListener {
            main.push(SetupFeaturesFragment())
        }
    }

    class SimplePagerAdapter(private val pages: List<Page>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<SimplePagerAdapter.VH>() {

        class VH(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val page = pages[position]
            val itemView = holder.itemView
            val image = itemView.findViewById<ImageView>(R.id.image)
            val density = itemView.resources.displayMetrics.density

            itemView.findViewById<TextView>(R.id.logo).visibility =
                if (position == 0) View.VISIBLE else View.GONE
            image.setImageResource(page.image)
            if (position == 0) {
                val mascotSize = (210 * density).toInt()
                image.layoutParams = FrameLayout.LayoutParams(mascotSize, mascotSize, Gravity.CENTER)
                image.setBackgroundResource(R.drawable.bg_circle_soft)
                val mascotPadding = (24 * density).toInt()
                image.setPadding(mascotPadding, mascotPadding, mascotPadding, mascotPadding)
            } else {
                image.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
                image.background = null
                image.setPadding(0, 0, 0, 0)
            }
            itemView.findViewById<TextView>(R.id.title).setText(page.title)
            itemView.findViewById<TextView>(R.id.body).setText(page.body)
        }

        override fun getItemCount() = pages.size
    }
}
