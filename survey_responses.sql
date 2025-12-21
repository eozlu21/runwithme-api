CREATE TABLE public.survey_responses (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    preferred_days VARCHAR(255),
    time_of_day VARCHAR(255),
    experience_level VARCHAR(255),
    activity_type VARCHAR(255),
    intensity_preference VARCHAR(255),
    social_vibe VARCHAR(255),
    motivation_type VARCHAR(255),
    coaching_style VARCHAR(255),
    music_preference VARCHAR(255),
    match_gender_preference BOOLEAN,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    FOREIGN KEY (user_id) REFERENCES public.users (user_id) ON DELETE CASCADE
);

CREATE INDEX idx_survey_responses_user_id ON public.survey_responses (user_id);

