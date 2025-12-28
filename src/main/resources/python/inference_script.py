#!/usr/bin/env python3
"""
Simple inference script called by Kotlin backend via ProcessBuilder.
No server needed - just reads input JSON, computes embedding, writes output JSON.

Usage:
    python inference_script.py --input route.json --output embedding.json --model-dir ./data
"""

import json
import argparse
import sys
import os

# Add parent directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import numpy as np
import torch


def load_model(model_dir: str):
    """Load the trained model."""
    from modular_features import (
        HybridRouteModelModular, 
        RouteCNN, 
        MetadataEncoderModular,
        FeatureRegistry
    )
    
    model_path = os.path.join(model_dir, "model.pt")
    checkpoint = torch.load(model_path, map_location='cpu', weights_only=False)
    
    cnn_model = RouteCNN(embedding_dim=128)
    metadata_model = MetadataEncoderModular(fusion_dim=128, embedding_dim=32)
    
    model = HybridRouteModelModular(cnn_model, metadata_model, fusion_dim=128)
    model.load_state_dict(checkpoint['model_state_dict'])
    model.eval()
    
    return model, checkpoint.get('feature_names', list(FeatureRegistry.get_all_features().keys()))


def create_path_image(route_data: dict, image_size: int = 64) -> torch.Tensor:
    """Create a path image from route geometry."""
    geometry = route_data.get('Geometry', route_data.get('geometry', {}))
    
    if isinstance(geometry, str):
        try:
            geometry = json.loads(geometry)
        except:
            geometry = {}
    
    coordinates = geometry.get('coordinates', [])
    
    if not coordinates or len(coordinates) < 2:
        return torch.zeros(1, 1, image_size, image_size)
    
    points = np.array(coordinates)
    
    # Normalize to [0, 1] range
    min_vals = points.min(axis=0)
    max_vals = points.max(axis=0)
    range_vals = max_vals - min_vals
    range_vals[range_vals == 0] = 1
    
    normalized = (points - min_vals) / range_vals
    
    # Scale to image coordinates
    padding = 2
    scale = image_size - 2 * padding
    img_coords = (normalized * scale + padding).astype(int)
    img_coords = np.clip(img_coords, 0, image_size - 1)
    
    # Create image
    image = np.zeros((image_size, image_size), dtype=np.float32)
    
    # Draw path
    for i in range(len(img_coords) - 1):
        x0, y0 = img_coords[i][:2]
        x1, y1 = img_coords[i + 1][:2]
        
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for t in range(steps + 1):
            x = int(x0 + (x1 - x0) * t / steps)
            y = int(y0 + (y1 - y0) * t / steps)
            if 0 <= x < image_size and 0 <= y < image_size:
                image[y, x] = 1.0
                for dx in [-1, 0, 1]:
                    for dy in [-1, 0, 1]:
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < image_size and 0 <= ny < image_size:
                            image[ny, nx] = max(image[ny, nx], 0.5)
    
    return torch.from_numpy(image).unsqueeze(0).unsqueeze(0)


def prepare_features(route_data: dict, feature_names: list) -> dict:
    """Prepare feature tensors from route data."""
    from modular_features import FeatureRegistry
    
    features_raw = FeatureRegistry.extract_features(route_data)
    feature_tensors = {}
    
    for feat_name in feature_names:
        feat_info = FeatureRegistry.get_all_features().get(feat_name)
        if feat_info is None:
            continue
        
        value = features_raw.get(feat_name)
        tensor = feat_info['tensor_converter'](value)
        feature_tensors[feat_name] = tensor.unsqueeze(0)
    
    return feature_tensors


def compute_embedding(model, route_data: dict, feature_names: list) -> dict:
    """Compute embeddings for a route."""
    with torch.no_grad():
        # Prepare inputs
        path_image = create_path_image(route_data)
        features = prepare_features(route_data, feature_names)
        
        # Run model
        fused, cnn_embed, metadata_embed, individual_embeddings = model(
            path_image, features, return_all=True
        )
        
        # Convert to dict of lists
        result = {
            'overall': fused.cpu().numpy().squeeze().tolist(),
            'shape': cnn_embed.cpu().numpy().squeeze().tolist(),
            'metadata': metadata_embed.cpu().numpy().squeeze().tolist()
        }
        
        for name, emb in individual_embeddings.items():
            result[name] = emb.cpu().numpy().squeeze().tolist()
        
        return result


def main():
    parser = argparse.ArgumentParser(description="Compute route embedding")
    parser.add_argument("--input", required=True, help="Input JSON file with route data")
    parser.add_argument("--output", required=True, help="Output JSON file for embeddings")
    parser.add_argument("--model-dir", required=True, help="Directory containing model.pt")
    
    args = parser.parse_args()
    
    try:
        # Load route data
        with open(args.input) as f:
            route_data = json.load(f)
        
        # Load model
        model, feature_names = load_model(args.model_dir)
        
        # Compute embeddings
        embeddings = compute_embedding(model, route_data, feature_names)
        
        # Write output
        with open(args.output, 'w') as f:
            json.dump(embeddings, f)
        
        print("OK")
        
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
