package com.kaikeventura.dsd;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public class ThirdPersonCameraController {

    private final Camera camera;
    private final ModelInstance target;

    // Configurações da Câmera
    private float distanceFromPlayer = 4.0f;
    private float angleAroundPlayer = 0; // Ângulo horizontal
    private float anglePitch = 20;       // Ângulo vertical

    // Variáveis de Suavização (Lerp)
    private final Vector3 targetCameraPosition = new Vector3();
    private final Vector3 playerPosition = new Vector3();
    private final float smoothSpeed = 5.0f;
    private final float mouseSensitivity = 0.2f;

    public ThirdPersonCameraController(Camera camera, ModelInstance target) {
        this.camera = camera;
        this.target = target;
        // Define a posição inicial da câmera sem suavização para evitar um "salto" no início
        updateCameraPosition(1f);
        camera.position.set(targetCameraPosition);
    }

    public void update(float delta) {
        // 1. Processar input do mouse para rotação
        float deltaX = -Gdx.input.getDeltaX() * mouseSensitivity;
        float deltaY = -Gdx.input.getDeltaY() * mouseSensitivity;

        angleAroundPlayer += deltaX;
        anglePitch += deltaY;
        anglePitch = MathUtils.clamp(anglePitch, -10f, 60f);

        // 2. Calcular a posição ALVO da câmera
        updateCameraPosition(delta);

        // 3. Suavizar (Lerp) a posição atual da câmera em direção à posição alvo
        camera.position.lerp(targetCameraPosition, delta * smoothSpeed);

        // 4. Apontar a câmera para o jogador
        target.transform.getTranslation(playerPosition);
        camera.lookAt(playerPosition);
        camera.update();
    }

    private void updateCameraPosition(float delta) {
        // Calcular as distâncias horizontal e vertical
        float horizontalDistance = (float) (distanceFromPlayer * Math.cos(Math.toRadians(anglePitch)));
        float verticalDistance = (float) (distanceFromPlayer * Math.sin(Math.toRadians(anglePitch)));

        // Calcular a posição da câmera em coordenadas esféricas
        float theta = angleAroundPlayer;
        float offsetX = (float) (horizontalDistance * Math.sin(Math.toRadians(theta)));
        float offsetZ = (float) (horizontalDistance * Math.cos(Math.toRadians(theta)));

        target.transform.getTranslation(playerPosition);

        targetCameraPosition.x = playerPosition.x - offsetX;
        targetCameraPosition.z = playerPosition.z - offsetZ;
        targetCameraPosition.y = playerPosition.y + verticalDistance;

        // Garantir que a câmera não entre no chão
        if (targetCameraPosition.y < 0.5f) {
            targetCameraPosition.y = 0.5f;
        }
    }
}
