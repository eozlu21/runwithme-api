"""
Modular Feature Encoding System for Route Similarity
=====================================================

This system uses abstract base classes to make adding features extremely easy!

Quick Start - Adding a New Feature:
-----------------------------------
# 1. For continuous features (single value):
class ElevationEncoder(ContinuousFeatureEncoder):
    '''Encoder for elevation gain (meters)'''
    pass  # That's it!

# 2. For categorical features (one-hot):
class WeatherEncoder(CategoricalFeatureEncoder):
    '''Encoder for weather conditions'''
    def __init__(self, embedding_dim=32):
        super().__init__(num_categories=5, embedding_dim=embedding_dim)

# 3. For multi-value features:
class WindEncoder(MultiValueFeatureEncoder):
    '''Encoder for wind (speed, direction_sin, direction_cos)'''
    def __init__(self, embedding_dim=32):
        super().__init__(num_values=3, embedding_dim=embedding_dim)

# Then add it to the model:
encoders = {
    'pace': PaceEncoder(),
    'terrain': TerrainEncoder(),
    'elevation': ElevationEncoder(),  # <-- Just one line!
}
"""

"""
MODULAR FEATURE ENCODING SYSTEM
================================

This module provides a fully modular system for adding features to route models.

🎯 KEY PRINCIPLE: Add features here, notebook stays unchanged!

HOW TO ADD A NEW FEATURE (4 steps):
===================================

1. Create encoder class (inherit from appropriate base):
   class YourFeatureEncoder(ContinuousFeatureEncoder):
       pass

2. Create extractor function:
   def extract_yourfeature(route: Dict) -> value:
       return route.get('your_field', default)

3. Create converter function:
   def yourfeature_to_tensor(value) -> torch.Tensor:
       return torch.tensor([value], dtype=torch.float32)

4. Register:
   FeatureRegistry.register('yourfeature', YourFeatureEncoder, 
                           extract_yourfeature, yourfeature_to_tensor)

That's it! The notebook will automatically:
- Extract your feature from routes
- Create your encoder
- Train on your feature
- Show similarities for your feature

BASE CLASSES:
=============
- ContinuousFeatureEncoder: For scalars (pace, distance, elevation)
- CategoricalFeatureEncoder: For categories (terrain, weather)
- MultiValueFeatureEncoder: For vectors (time, coordinates)

See README_NEW.md for detailed examples and documentation.
"""

import torch
import torch.nn as nn
import torch.nn.functional as F
from typing import Dict, List, Tuple, Callable, Any
from abc import ABC, abstractmethod
import numpy as np
from PIL import Image
from torchvision import transforms
import json


# ============================================================================
# FEATURE REGISTRY (Automatic Feature Discovery)
# ============================================================================

class FeatureRegistry:
    """
    Central registry for all features.
    Features self-register when their encoder class is defined.
    """
    _features = {}
    
    @classmethod
    def register(cls, name: str, encoder_class, extractor: Callable, tensor_converter: Callable):
        """
        Register a feature with its encoder and extraction logic.
        
        Args:
            name: Feature name (e.g., 'pace', 'terrain')
            encoder_class: Encoder class (not instance!)
            extractor: Function to extract feature from route dict
            tensor_converter: Function to convert extracted value to tensor
        """
        cls._features[name] = {
            'encoder_class': encoder_class,
            'extractor': extractor,
            'tensor_converter': tensor_converter
        }
    
    @classmethod
    def get_all_features(cls) -> Dict:
        """Get all registered features"""
        return cls._features.copy()
    
    @classmethod
    def create_encoders(cls, embedding_dim: int = 32) -> Dict[str, nn.Module]:
        """Create encoder instances for all registered features"""
        return {
            name: info['encoder_class'](embedding_dim=embedding_dim)
            for name, info in cls._features.items()
        }
    
    @classmethod
    def extract_features(cls, route: Dict) -> Dict[str, Any]:
        """Extract all features from a route"""
        return {
            name: info['extractor'](route)
            for name, info in cls._features.items()
        }
    
    @classmethod
    def convert_to_tensors(cls, features: Dict) -> Dict[str, torch.Tensor]:
        """Convert extracted features to tensors"""
        return {
            name: cls._features[name]['tensor_converter'](value)
            for name, value in features.items()
            if name in cls._features
        }


# ============================================================================
# ABSTRACT BASE CLASSES
# ============================================================================

class BaseFeatureEncoder(nn.Module, ABC):
    """
    Abstract base class for all feature encoders.
    
    Provides:
    - Automatic input shape handling
    - L2 normalization
    - Common interface for all encoders
    
    Subclasses only need to implement _build_encoder()
    """
    def __init__(self, embedding_dim=32):
        super(BaseFeatureEncoder, self).__init__()
        self.embedding_dim = embedding_dim
        self.encoder = self._build_encoder()
    
    @abstractmethod
    def _build_encoder(self):
        """
        Build and return the encoder architecture.
        Must be implemented by subclasses.
        
        Returns:
            nn.Sequential or nn.Module
        """
        pass
    
    def _handle_input_shape(self, x):
        """
        Handle both (batch,) and (batch, features) input shapes.
        
        Args:
            x: Input tensor
        
        Returns:
            x: Reshaped to (batch, features)
        """
        if x.dim() == 1:
            x = x.unsqueeze(1)
        return x
    
    def forward(self, x):
        """
        Forward pass with automatic shape handling and L2 normalization.
        
        Args:
            x: Input tensor of shape (batch,) or (batch, features)
        
        Returns:
            embedding: L2-normalized embedding of shape (batch, embedding_dim)
        """
        x = self._handle_input_shape(x)
        x = self.encoder(x)
        x = F.normalize(x, p=2, dim=1)
        return x


class ContinuousFeatureEncoder(BaseFeatureEncoder):
    """
    Base encoder for continuous (scalar) features.
    
    Use for: pace, distance, elevation, temperature, speed, heart rate, etc.
    
    Architecture:
        Input (1) → FC(64) → BatchNorm → ReLU → Dropout → FC(embedding_dim)
    
    Args:
        embedding_dim: Output embedding dimension (default: 32)
        hidden_dim: Hidden layer dimension (default: 64)
        dropout: Dropout probability (default: 0.2)
    """
    def __init__(self, embedding_dim=32, hidden_dim=64, dropout=0.2):
        self.hidden_dim = hidden_dim
        self.dropout = dropout
        super(ContinuousFeatureEncoder, self).__init__(embedding_dim)
    
    def _build_encoder(self):
        return nn.Sequential(
            nn.Linear(1, self.hidden_dim),
            nn.BatchNorm1d(self.hidden_dim),
            nn.ReLU(),
            nn.Dropout(self.dropout),
            nn.Linear(self.hidden_dim, self.embedding_dim)
        )


class CategoricalFeatureEncoder(BaseFeatureEncoder):
    """
    Base encoder for categorical (one-hot encoded) features.
    
    Use for: terrain type, weather condition, surface type, difficulty, etc.
    
    Architecture:
        Input (num_categories) → FC(64) → BatchNorm → ReLU → Dropout → FC(embedding_dim)
    
    Args:
        num_categories: Number of categories (one-hot vector size)
        embedding_dim: Output embedding dimension (default: 32)
        hidden_dim: Hidden layer dimension (default: 64)
        dropout: Dropout probability (default: 0.2)
    """
    def __init__(self, num_categories, embedding_dim=32, hidden_dim=64, dropout=0.2):
        self.num_categories = num_categories
        self.hidden_dim = hidden_dim
        self.dropout = dropout
        super(CategoricalFeatureEncoder, self).__init__(embedding_dim)
    
    def _build_encoder(self):
        return nn.Sequential(
            nn.Linear(self.num_categories, self.hidden_dim),
            nn.BatchNorm1d(self.hidden_dim),
            nn.ReLU(),
            nn.Dropout(self.dropout),
            nn.Linear(self.hidden_dim, self.embedding_dim)
        )
    
    def _handle_input_shape(self, x):
        """Categorical features are already (batch, num_categories), no reshaping needed"""
        return x


class MultiValueFeatureEncoder(BaseFeatureEncoder):
    """
    Base encoder for multi-value continuous features.
    
    Use for: GPS coordinates (lat, lon), time encoding (sin, cos), wind (speed, direction)
    
    Architecture:
        Input (num_values) → FC(64) → BatchNorm → ReLU → Dropout → FC(embedding_dim)
    
    Args:
        num_values: Number of input values
        embedding_dim: Output embedding dimension (default: 32)
        hidden_dim: Hidden layer dimension (default: 64)
        dropout: Dropout probability (default: 0.2)
    """
    def __init__(self, num_values, embedding_dim=32, hidden_dim=64, dropout=0.2):
        self.num_values = num_values
        self.hidden_dim = hidden_dim
        self.dropout = dropout
        super(MultiValueFeatureEncoder, self).__init__(embedding_dim)
    
    def _build_encoder(self):
        return nn.Sequential(
            nn.Linear(self.num_values, self.hidden_dim),
            nn.BatchNorm1d(self.hidden_dim),
            nn.ReLU(),
            nn.Dropout(self.dropout),
            nn.Linear(self.hidden_dim, self.embedding_dim)
        )
    
    def _handle_input_shape(self, x):
        """Multi-value features are already (batch, num_values), no reshaping needed"""
        return x


# ============================================================================
# CONCRETE ENCODER IMPLEMENTATIONS
# ============================================================================

class PaceEncoder(ContinuousFeatureEncoder):
    """Encoder for running pace (min/km)"""
    pass


class DistanceEncoder(ContinuousFeatureEncoder):
    """Encoder for route distance (km)"""
    pass


class TerrainEncoder(CategoricalFeatureEncoder):
    """
    Encoder for terrain type (one-hot encoded)
    
    Categories: urban, suburb, park, trail, forest, coast, hills, mountain, mixed
    
    Args:
        embedding_dim: Output embedding dimension (default: 32)
    """
    def __init__(self, embedding_dim=32):
        super(TerrainEncoder, self).__init__(
            num_categories=9,
            embedding_dim=embedding_dim
        )


class TimeEncoder(MultiValueFeatureEncoder):
    """
    Encoder for time of day using cyclic encoding
    
    Converts time to (sin, cos) representation to capture cyclical nature
    
    Args:
        embedding_dim: Output embedding dimension (default: 32)
    """
    def __init__(self, embedding_dim=32):
        super(TimeEncoder, self).__init__(
            num_values=2,  # sin and cos
            embedding_dim=embedding_dim
        )


class RunnerTypeEncoder(CategoricalFeatureEncoder):
    """
    Encoder for runner type (one-hot encoded)
    
    Categories: elite, fast, average, slow_jog, fast_walk, casual_walk
    
    Args:
        embedding_dim: Output embedding dimension (default: 32)
    """
    def __init__(self, embedding_dim=32):
        super(RunnerTypeEncoder, self).__init__(
            num_categories=6,
            embedding_dim=embedding_dim
        )


class PreferredDayEncoder(CategoricalFeatureEncoder):
    """
    Encoder for preferred day of the week (one-hot encoded)
    
    Categories: monday, tuesday, wednesday, thursday, friday, saturday, sunday
    
    Args:
        embedding_dim: Output embedding dimension (default: 32)
    """
    def __init__(self, embedding_dim=32):
        super(PreferredDayEncoder, self).__init__(
            num_categories=7,
            embedding_dim=embedding_dim
        )


class TimeOfDayEncoder(CategoricalFeatureEncoder):
    """
    Encoder for time of day category (one-hot encoded)
    
    Categories: early_bird (5-7am), morning (7-11am), lunch (11am-2pm), 
                afternoon (2-5pm), evening (5-8pm), night (8pm-5am)
    
    Args:
        embedding_dim: Output embedding dimension (default: 32)
    """
    def __init__(self, embedding_dim=32):
        super(TimeOfDayEncoder, self).__init__(
            num_categories=6,
            embedding_dim=embedding_dim
        )


# ============================================================================
# USER PREFERENCE FEATURES (NOT in ML model - simple matching in backend)
# ============================================================================
# These are handled by simple exact matching in Kotlin backend:
# - experience_level: beginner, amateur, intermediate, professional
# - activity_type: walking, hiking, leisure, competitive
# - intensity_preference: high, steady
# - social_vibe: silent, social
# - motivation: mental, weightloss, training, socializing
# - coaching_style: pusher, companion
# - music_preference: headphone, nature
# - match_gender: true, false
# ============================================================================


# ============================================================================
# FEATURE REGISTRATION (Add your features here!)
# ============================================================================

# Terrain mapping (Route features - used by ML model)
TERRAIN_TYPES = ['urban', 'suburb', 'park', 'trail', 'forest', 'coast', 'hills', 'mountain', 'mixed']
RUNNER_TYPES = ['elite', 'fast', 'average', 'slow_jog', 'fast_walk', 'casual_walk']
DAYS_OF_WEEK = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday']
TIME_OF_DAY_CATEGORIES = ['early_bird', 'morning', 'lunch', 'afternoon', 'evening', 'night']

def extract_pace(route: Dict) -> float:
    """Extract pace from route (accepts PaceMinPerKm or Pace)"""
    return route.get('PaceMinPerKm', route.get('Pace', 5.0))

def extract_terrain(route: Dict) -> str:
    """Extract terrain type from route"""
    return route.get('Terrain', 'urban')

def extract_distance(route: Dict) -> float:
    """Extract distance from route (convert to km)"""
    distance_m = route.get('Distance', 5000.0)
    return distance_m / 1000.0

def extract_time(route: Dict) -> float:
    """Extract time of day from route (hour 0-24).
    
    Accepts:
    - 'TimeOfDay': Hour as float (e.g., 6.5 for 6:30am)
    - 'StartTime': Time of day as 'HH:MM' string
    """
    # Direct hour value
    if 'TimeOfDay' in route:
        return float(route['TimeOfDay'])
    
    # Parse from StartTime string
    start_time = route.get('StartTime', '12:00')
    if isinstance(start_time, str) and ':' in start_time:
        try:
            parts = start_time.split(':')
            hour = int(parts[0])
            minute = int(parts[1]) if len(parts) > 1 else 0
            return hour + minute / 60.0
        except:
            pass
    return 12.0

def pace_to_tensor(pace: float) -> torch.Tensor:
    """Convert pace to tensor"""
    return torch.tensor([pace], dtype=torch.float32)

def terrain_to_tensor(terrain: str) -> torch.Tensor:
    """Convert terrain type to one-hot tensor"""
    idx = TERRAIN_TYPES.index(terrain) if terrain in TERRAIN_TYPES else 0
    one_hot = torch.zeros(len(TERRAIN_TYPES), dtype=torch.float32)
    one_hot[idx] = 1.0
    return one_hot

def distance_to_tensor(distance: float) -> torch.Tensor:
    """Convert distance to tensor"""
    return torch.tensor([distance], dtype=torch.float32)

def time_to_tensor(hour: float) -> torch.Tensor:
    """Convert time of day (hour) to cyclic encoding (sin, cos).
    
    This captures that 23:00 and 01:00 are close together.
    """
    angle = 2 * np.pi * hour / 24
    return torch.tensor([np.sin(angle), np.cos(angle)], dtype=torch.float32)

def extract_runner_type(route: Dict) -> str:
    """Extract runner type from route"""
    return route.get('RunnerType', 'average')

def runner_type_to_tensor(runner_type: str) -> torch.Tensor:
    """Convert runner type to one-hot tensor"""
    idx = RUNNER_TYPES.index(runner_type) if runner_type in RUNNER_TYPES else 2  # default to 'average'
    one_hot = torch.zeros(len(RUNNER_TYPES), dtype=torch.float32)
    one_hot[idx] = 1.0
    return one_hot


def extract_preferred_day(route: Dict) -> str:
    """Extract preferred day of the week from route filename or metadata"""
    # Try to get from route metadata first
    preferred_day = route.get('PreferredDay', None)
    if preferred_day and preferred_day.lower() in DAYS_OF_WEEK:
        return preferred_day.lower()
    
    # Try to parse from filename (e.g., "2022_07_20T01_18_27.591242Z.gpx")
    filename = route.get('Filename', '')
    if filename:
        try:
            # Parse date from filename format: YYYY_MM_DD
            parts = filename.split('_')
            if len(parts) >= 3:
                year = int(parts[0])
                month = int(parts[1])
                day = int(parts[2].split('T')[0])
                
                # Calculate day of week (0=Monday, 6=Sunday)
                import datetime
                date = datetime.date(year, month, day)
                day_idx = date.weekday()
                return DAYS_OF_WEEK[day_idx]
        except:
            pass
    
    return 'monday'  # default


def preferred_day_to_tensor(day: str) -> torch.Tensor:
    """Convert preferred day to one-hot tensor"""
    idx = DAYS_OF_WEEK.index(day.lower()) if day.lower() in DAYS_OF_WEEK else 0
    one_hot = torch.zeros(len(DAYS_OF_WEEK), dtype=torch.float32)
    one_hot[idx] = 1.0
    return one_hot


def extract_time_of_day(route: Dict) -> str:
    """
    Extract time of day category from route
    
    Categories:
    - early_bird: 5am - 7am
    - morning: 7am - 11am
    - lunch: 11am - 2pm
    - afternoon: 2pm - 5pm
    - evening: 5pm - 8pm
    - night: 8pm - 5am
    """
    # Try to get from route metadata first
    time_of_day = route.get('TimeOfDay', None)
    if time_of_day and time_of_day.lower() in TIME_OF_DAY_CATEGORIES:
        return time_of_day.lower()
    
    # Try to parse hour from filename or StartTime
    hour = 12  # default
    
    # Try StartTime first
    start_time = route.get('StartTime', '')
    if isinstance(start_time, str) and ':' in start_time:
        try:
            hour = int(start_time.split(':')[0])
        except:
            pass
    else:
        # Try to parse from filename (e.g., "2022_07_20T01_18_27.591242Z.gpx")
        filename = route.get('Filename', '')
        if 'T' in filename:
            try:
                time_part = filename.split('T')[1]
                hour = int(time_part[:2])
            except:
                pass
    
    # Classify into time of day categories
    if 5 <= hour < 7:
        return 'early_bird'
    elif 7 <= hour < 11:
        return 'morning'
    elif 11 <= hour < 14:
        return 'lunch'
    elif 14 <= hour < 17:
        return 'afternoon'
    elif 17 <= hour < 20:
        return 'evening'
    else:  # 20-5
        return 'night'


def time_of_day_to_tensor(time_of_day: str) -> torch.Tensor:
    """Convert time of day category to one-hot tensor"""
    idx = TIME_OF_DAY_CATEGORIES.index(time_of_day.lower()) if time_of_day.lower() in TIME_OF_DAY_CATEGORIES else 1  # default to 'morning'
    one_hot = torch.zeros(len(TIME_OF_DAY_CATEGORIES), dtype=torch.float32)
    one_hot[idx] = 1.0
    return one_hot


# Register all ROUTE features (ML model handles these)
# Note: preferred_day, time_of_day, and runner_type moved to simple preference matching in backend
FeatureRegistry.register('pace', PaceEncoder, extract_pace, pace_to_tensor)
FeatureRegistry.register('terrain', TerrainEncoder, extract_terrain, terrain_to_tensor)
FeatureRegistry.register('distance', DistanceEncoder, extract_distance, distance_to_tensor)
FeatureRegistry.register('time', TimeEncoder, extract_time, time_to_tensor)
# FeatureRegistry.register('runner_type', RunnerTypeEncoder, extract_runner_type, runner_type_to_tensor)
# FeatureRegistry.register('preferred_day', PreferredDayEncoder, extract_preferred_day, preferred_day_to_tensor)
# FeatureRegistry.register('time_of_day', TimeOfDayEncoder, extract_time_of_day, time_of_day_to_tensor)


# ============================================================================
# TO ADD A NEW FEATURE:
# ============================================================================
# 1. Create encoder class (inherit from appropriate base class)
# 2. Create extractor function: extract_yourfeature(route) -> value
# 3. Create converter function: yourfeature_to_tensor(value) -> torch.Tensor
# 4. Register: FeatureRegistry.register('yourfeature', YourEncoder, extract_yourfeature, yourfeature_to_tensor)
# 
# That's it! The dataset and model will automatically pick it up.
# ============================================================================


# ============================================================================
# MODULAR DATASET (Automatically uses registered features)
# ============================================================================

class HybridDatasetModular:
    """
    Dataset that automatically uses all registered features.
    No need to modify when adding new features!
    """
    def __init__(self, jsonl_path, num_routes=1000, image_gen_func=None, augmenter=None):
        """
        Args:
            jsonl_path: Path to JSONL file with routes
            num_routes: Number of routes to load
            image_gen_func: Function to generate image from geometry (e.g., route_to_image)
            augmenter: Optional augmentation function for images
        """
        self.jsonl_path = jsonl_path
        self.num_routes = num_routes
        self.image_gen_func = image_gen_func
        self.augmenter = augmenter
        
        # Image transforms
        self.transform = transforms.Compose([
            transforms.ToTensor(),
        ])
        
        # Load routes with all necessary data
        print(f"Loading {num_routes} routes with images and metadata...")
        self.routes = []
        
        with open(jsonl_path, 'r') as f:
            for i, line in enumerate(f):
                if i >= num_routes:
                    break
                route = json.loads(line)
                geometry = route.get('geometry', [])
                
                # Generate image if function provided
                if image_gen_func and geometry and len(geometry) >= 2:
                    img = image_gen_func(geometry, img_size=64)
                    if img is not None:
                        # Store raw route data - extractors will pull what they need
                        self.routes.append({
                            'idx': i,
                            'img': img,
                            'route_data': route  # Store full route for feature extraction
                        })
        
        print(f"✅ Loaded {len(self.routes)} valid routes with images + metadata")
        print(f"📋 Registered features: {list(FeatureRegistry.get_all_features().keys())}")
    
    def __len__(self):
        return len(self.routes) * 2  # Pairs for contrastive learning
    
    def __getitem__(self, idx):
        """
        Returns pairs of (image, features) for contrastive learning
        """
        # Generate positive or negative pairs
        if idx % 2 == 0:
            # Positive pair (same route)
            route_idx = (idx // 2) % len(self.routes)
            route1 = self.routes[route_idx]
            route2 = route1
            label = torch.tensor(0.0)
        else:
            # Negative pair (different routes)
            route_idx1 = (idx // 2) % len(self.routes)
            route1 = self.routes[route_idx1]
            route2_idx = (route_idx1 + np.random.randint(1, len(self.routes))) % len(self.routes)
            route2 = self.routes[route2_idx]
            label = torch.tensor(1.0)
        
        # Process route 1
        if self.augmenter:
            # Augmenter handles conversion to tensor
            img1 = self.augmenter(route1['img'])
        else:
            # No augmenter, just convert to tensor
            img1 = self.transform(route1['img'])
        features_raw1 = FeatureRegistry.extract_features(route1['route_data'])
        features1 = FeatureRegistry.convert_to_tensors(features_raw1)
        
        # Process route 2
        if self.augmenter:
            # Augmenter handles conversion to tensor
            img2 = self.augmenter(route2['img'])
        else:
            # No augmenter, just convert to tensor
            img2 = self.transform(route2['img'])
        features_raw2 = FeatureRegistry.extract_features(route2['route_data'])
        features2 = FeatureRegistry.convert_to_tensors(features_raw2)
        
        return (img1, features1), (img2, features2), label


# ============================================================================
# CNN MODEL FOR ROUTE IMAGES
# ============================================================================

class RouteCNN(nn.Module):
    """
    Simple CNN to extract features from route images.
    Input: (batch, 1, 64, 64) grayscale route images
    Output: (batch, embedding_dim) embedding vectors
    """
    def __init__(self, embedding_dim=128):
        super(RouteCNN, self).__init__()
        
        # Convolutional layers
        self.conv1 = nn.Conv2d(1, 32, kernel_size=5, stride=1, padding=2)
        self.bn1 = nn.BatchNorm2d(32)
        self.pool1 = nn.MaxPool2d(2, 2)  # 64 -> 32
        
        self.conv2 = nn.Conv2d(32, 64, kernel_size=3, stride=1, padding=1)
        self.bn2 = nn.BatchNorm2d(64)
        self.pool2 = nn.MaxPool2d(2, 2)  # 32 -> 16
        
        self.conv3 = nn.Conv2d(64, 128, kernel_size=3, stride=1, padding=1)
        self.bn3 = nn.BatchNorm2d(128)
        self.pool3 = nn.MaxPool2d(2, 2)  # 16 -> 8
        
        # Fully connected layers
        self.fc1 = nn.Linear(128 * 8 * 8, 512)
        self.dropout = nn.Dropout(0.3)
        self.fc2 = nn.Linear(512, embedding_dim)
    
    def forward(self, x):
        # Convolutional layers with ReLU and pooling
        x = self.pool1(F.relu(self.bn1(self.conv1(x))))
        x = self.pool2(F.relu(self.bn2(self.conv2(x))))
        x = self.pool3(F.relu(self.bn3(self.conv3(x))))
        
        # Flatten
        x = x.view(x.size(0), -1)
        
        # Fully connected layers
        x = F.relu(self.fc1(x))
        x = self.dropout(x)
        x = self.fc2(x)
        
        # L2 normalization for better contrastive learning
        x = F.normalize(x, p=2, dim=1)
        
        return x


# ============================================================================
# FUSION MODEL
# ============================================================================

class MetadataEncoderModular(nn.Module):
    """
    Modular metadata encoder that combines multiple feature encoders.
    
    Automatically uses all registered features from FeatureRegistry.
    """
    def __init__(self, fusion_dim=128, embedding_dim=32):
        """
        Args:
            fusion_dim: Dimension of final fused embedding
            embedding_dim: Dimension for each feature embedding
        """
        super().__init__()
        
        # Automatically create encoders for all registered features
        self.encoders = nn.ModuleDict(FeatureRegistry.create_encoders(embedding_dim))
        self.feature_names = list(self.encoders.keys())
        
        # Calculate total embedding dimension from all encoders
        total_dim = sum(enc.embedding_dim for enc in self.encoders.values())
        
        # Fusion network: combines all feature embeddings
        self.fusion = nn.Sequential(
            nn.Linear(total_dim, 256),
            nn.BatchNorm1d(256),
            nn.ReLU(),
            nn.Dropout(0.3),
            nn.Linear(256, fusion_dim)
        )
    
    def forward(self, features: Dict[str, torch.Tensor], return_individual=False):
        """
        Args:
            features: Dictionary of {feature_name: tensor}
                     e.g., {'pace': tensor, 'terrain': tensor, ...}
            return_individual: If True, return individual feature embeddings

        Returns:
            If return_individual=False:
                fused_embedding: (batch, fusion_dim) - combined embedding
            If return_individual=True:
                (fused_embedding, individual_embeddings_dict)
        """
        # Encode each feature individually
        individual_embeddings = {}
        for name, encoder in self.encoders.items():
            if name in features:
                individual_embeddings[name] = encoder(features[name])

        # Concatenate all embeddings
        all_embeddings = torch.cat([individual_embeddings[name] for name in self.feature_names], dim=1)

        # Fuse into final embedding
        fused = self.fusion(all_embeddings)
        fused = F.normalize(fused, p=2, dim=1)

        if return_individual:
            return fused, individual_embeddings
        return fused


# ============================================================================
# MODULAR HYBRID MODEL
# ============================================================================

class HybridRouteModelModular(nn.Module):
    """
    Hybrid model combining CNN (shape) + Modular Metadata Encoder.

    Provides:
    - Overall similarity (shape + metadata fused)
    - Shape-only similarity
    - Metadata-only similarity
    - Individual feature similarities (pace, terrain, distance, time, etc.)
    """
    def __init__(self, cnn_model, metadata_model, fusion_dim=128):
        super().__init__()

        self.cnn = cnn_model
        self.metadata = metadata_model

        # Final fusion: combine CNN and metadata embeddings
        self.fusion = nn.Sequential(
            nn.Linear(128 + fusion_dim, 256),
            nn.BatchNorm1d(256),
            nn.ReLU(),
            nn.Dropout(0.3),
            nn.Linear(256, fusion_dim)
        )

    def forward(self, img, features: Dict[str, torch.Tensor], return_all=False):
        """
        Args:
            img: (batch, 1, H, W) - route image
            features: Dict of feature tensors
            return_all: If True, return all embeddings (fused, cnn, metadata, individual features)

        Returns:
            If return_all=False:
                fused_embedding: (batch, fusion_dim)
            If return_all=True:
                (fused, cnn_embed, metadata_embed, individual_embeddings_dict)
        """
        # Get CNN embedding (shape)
        cnn_embed = self.cnn(img)

        # Get metadata embedding (combined) and individual feature embeddings
        metadata_embed, individual_embeddings = self.metadata(features, return_individual=True)

        # Fuse CNN and metadata
        combined = torch.cat([cnn_embed, metadata_embed], dim=1)
        fused = self.fusion(combined)
        fused = F.normalize(fused, p=2, dim=1)

        if return_all:
            return fused, cnn_embed, metadata_embed, individual_embeddings
        return fused


# ============================================================================
# SIMILARITY CALCULATOR
# ============================================================================

class SimilarityCalculator:
    """
    Utility class for computing similarities between routes.

    Computes:
    - Overall similarity
    - Shape similarity (CNN)
    - Metadata similarity (MLP)
    - Individual feature similarities (pace, terrain, distance, time, etc.)
    """

    @staticmethod
    def compute_all_similarities(
        query_embeddings: Dict[str, np.ndarray],
        all_embeddings: Dict[str, np.ndarray],
        query_idx: int
    ) -> Dict[str, np.ndarray]:
        """
        Compute similarity scores for all embedding types.

        Args:
            query_embeddings: Dict of {name: embeddings_array} for all routes
            all_embeddings: Same as query_embeddings (for consistency)
            query_idx: Index of the query route

        Returns:
            Dict of {similarity_name: similarity_scores_array}
            Each array is shape (num_routes,) with values 0-100%
        """
        similarities = {}

        for name, embeddings in all_embeddings.items():
            query_embed = embeddings[query_idx:query_idx+1]  # Keep dims

            # Compute Euclidean distances
            distances = np.linalg.norm(embeddings - query_embed, axis=1)

            # Convert to similarity scores (0-100%)
            # Using exponential decay: similarity = 100 * exp(-distance)
            max_dist = distances.max() if distances.max() > 0 else 1.0
            similarities[name] = 100 * np.exp(-distances / max_dist)

        return similarities

    @staticmethod
    def calculate_all_similarities(
        all_embeddings: Dict[str, np.ndarray],
        query_idx: int,
        include_self: bool = True
    ) -> Dict[str, np.ndarray]:
        """
        Compute similarity scores for all embedding types.
        Alias for compute_all_similarities with simpler interface.

        Args:
            all_embeddings: Dict of {name: embeddings_array} for all routes
            query_idx: Index of the query route
            include_self: Whether to include query route in results (default: True)

        Returns:
            Dict of {similarity_name: similarity_scores_array}
            Each array is shape (num_routes,) with values 0-100%
        """
        similarities = {}

        for name, embeddings in all_embeddings.items():
            query_embed = embeddings[query_idx:query_idx+1]  # Keep dims

            # Compute Euclidean distances
            distances = np.linalg.norm(embeddings - query_embed, axis=1)

            # Convert to similarity scores (0-100%)
            # Using exponential decay: similarity = 100 * exp(-distance)
            max_dist = distances.max() if distances.max() > 0 else 1.0
            similarities[name] = 100 * np.exp(-distances / max_dist)
            
            # Optionally exclude query itself
            if not include_self:
                similarities[name][query_idx] = -1  # Mark for exclusion

        return similarities

    @staticmethod
    def get_top_matches(
        similarities: Dict[str, np.ndarray],
        query_idx: int,
        top_k: int = 10,
        primary_metric: str = 'overall'
    ) -> List[Tuple[int, Dict[str, float]]]:
        """
        Get top K most similar routes based on primary metric.

        Args:
            similarities: Dict of {name: similarity_scores}
            query_idx: Index of query route (will be excluded)
            top_k: Number of matches to return
            primary_metric: Which similarity to use for ranking (default: 'overall')

        Returns:
            List of (route_idx, {metric_name: similarity_score}) tuples
        """
        # Get indices sorted by primary metric
        primary_scores = similarities[primary_metric]
        sorted_indices = np.argsort(primary_scores)[::-1]  # Descending order

        # Exclude query itself
        sorted_indices = [idx for idx in sorted_indices if idx != query_idx]

        # Get top K
        top_indices = sorted_indices[:top_k]

        # Collect all similarity scores for each match
        matches = []
        for idx in top_indices:
            scores = {name: float(scores[idx]) for name, scores in similarities.items()}
            matches.append((idx, scores))

        return matches

    @staticmethod
    def print_similarity_breakdown(
        route_idx: int,
        similarities: Dict[str, float],
        route_metadata: Dict = None
    ):
        """
        Pretty-print similarity breakdown for a route.

        Args:
            route_idx: Index of the route
            similarities: Dict of {metric_name: similarity_score}
            route_metadata: Optional dict with route info (pace, terrain, distance, etc.)
        """
        print(f"Route #{route_idx}")

        if route_metadata:
            if 'terrain' in route_metadata:
                print(f"  Terrain: {route_metadata['terrain'].title()}")
            if 'pace' in route_metadata:
                print(f"  Pace: {route_metadata['pace']:.2f} min/km")
            if 'distance' in route_metadata:
                print(f"  Distance: {route_metadata['distance']:.2f} km")
            if 'time_str' in route_metadata:
                print(f"  Time: {route_metadata['time_str']}")

        print(f"  Similarities:")
        print(f"    Overall: {similarities.get('overall', 0):.1f}%")
        print(f"    Shape: {similarities.get('shape', 0):.1f}%")
        print(f"    Metadata (combined): {similarities.get('metadata', 0):.1f}%")
        print(f"    Individual Features:")
        for feature in ['pace', 'terrain', 'distance', 'time']:
            if feature in similarities:
                print(f"      {feature.title()}: {similarities[feature]:.1f}%")


# ============================================================================
# EXAMPLE USAGE
# ============================================================================

def create_modular_model(cnn_model, device='cpu'):
    """
    Helper function to create a modular hybrid model.

    Args:
        cnn_model: Pre-trained or new RouteCNN model
        device: 'cpu' or 'cuda'

    Returns:
        HybridRouteModelModular instance
    """
    # Create individual feature encoders
    encoders = {
        'pace': PaceEncoder(embedding_dim=32),
        'terrain': TerrainEncoder(num_terrains=9, embedding_dim=32),
        'distance': DistanceEncoder(embedding_dim=32),
        'time': TimeEncoder(embedding_dim=32),
    }

    # Create modular metadata encoder
    metadata_model = MetadataEncoderModular(encoders, fusion_dim=128)

    # Create hybrid model
    hybrid_model = HybridRouteModelModular(cnn_model, metadata_model, fusion_dim=128)

    return hybrid_model.to(device)


if __name__ == "__main__":
    # Example: Test the modular architecture
    print("🧪 Testing Modular Feature Encoding System\n")
    
    # Test feature registry
    print("📋 Registered Features:")
    for name in FeatureRegistry.get_all_features():
        print(f"  - {name}")
    
    # Create metadata encoder (automatically uses all registered features)
    metadata_encoder = MetadataEncoderModular(fusion_dim=128, embedding_dim=32)
    
    print(f"\n✅ Encoder ready with {len(metadata_encoder.encoders)} features")
    print(f"✨ Total parameters: {sum(p.numel() for p in metadata_encoder.parameters()):,}")
    print("\n💡 To add a new feature:")
    print("   1. Create encoder class (inherit from base class)")
    print("   2. Create extractor and converter functions")
    print("   3. Register with FeatureRegistry.register()")
    print("   4. That's it! No notebook changes needed.")

