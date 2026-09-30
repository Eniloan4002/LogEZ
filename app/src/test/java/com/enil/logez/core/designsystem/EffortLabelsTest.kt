package com.enil.logez.core.designsystem

import com.enil.logez.R
import com.enil.logez.core.domain.model.EffortScale
import com.enil.logez.core.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Test

/** P-211: which strings.xml copy an effort value, a scale and a set type map to. */
class EffortLabelsTest {
    @Test
    fun `each picker value has the README's plain words, shared by both scales`() {
        assertEquals(R.string.effort_caption_rpe_10, effortCaptionRes(10.0))
        assertEquals(R.string.effort_caption_rpe_9_5, effortCaptionRes(9.5))
        assertEquals(R.string.effort_caption_rpe_9, effortCaptionRes(9.0))
        assertEquals(R.string.effort_caption_rpe_8_5, effortCaptionRes(8.5))
        assertEquals(R.string.effort_caption_rpe_8, effortCaptionRes(8.0))
        assertEquals(R.string.effort_caption_rpe_7_5, effortCaptionRes(7.5))
        assertEquals(R.string.effort_caption_rpe_7, effortCaptionRes(7.0))
        assertEquals(R.string.effort_caption_rpe_6, effortCaptionRes(6.0))
    }

    @Test
    fun `off-scale values still get a caption`() {
        assertEquals(R.string.effort_caption_rpe_6_5, effortCaptionRes(6.5))
        assertEquals(R.string.effort_caption_rpe_6, effortCaptionRes(5.0))
        assertEquals(R.string.effort_caption_rpe_10, effortCaptionRes(10.5))
        assertEquals(R.string.effort_caption_rpe_8_5, effortCaptionRes(8.25))
    }

    @Test
    fun `each scale has its own label, legend, picker and explainer copy`() {
        assertEquals(R.string.effort_scale_rpe, EffortScale.RPE.labelRes())
        assertEquals(R.string.effort_scale_rir, EffortScale.RIR.labelRes())
        assertEquals(R.string.legend_effort_rir, EffortScale.RIR.legendRes())
        assertEquals(R.string.effort_picker_title_rir, EffortScale.RIR.pickerTitleRes())
        assertEquals(R.string.effort_picker_ask_rpe, EffortScale.RPE.pickerAskRes())
        assertEquals(R.string.effort_picker_link_rir, EffortScale.RIR.pickerLinkRes())
        assertEquals(R.string.effort_explainer_title_rpe, EffortScale.RPE.explainerTitleRes())
        assertEquals(R.string.effort_explainer_body_rir, EffortScale.RIR.explainerBodyRes())
    }

    @Test
    fun `the explainer's hint names the other scale`() {
        assertEquals(R.string.effort_explainer_switch_to_rir, EffortScale.RPE.explainerSwitchHintRes())
        assertEquals(R.string.effort_explainer_switch_to_rpe, EffortScale.RIR.explainerSwitchHintRes())
    }

    @Test
    fun `a numbered set keeps the logger's field label, a lettered one says its type`() {
        assertEquals(R.string.workout_set_field_a11y, setFieldA11yRes(SetType.NORMAL))
        assertEquals(R.string.set_field_a11y_warmup, setFieldA11yRes(SetType.WARMUP))
        assertEquals(R.string.set_field_a11y_failure, setFieldA11yRes(SetType.FAILURE))
        assertEquals(R.string.set_field_a11y_dropset, setFieldA11yRes(SetType.DROPSET))
    }
}
