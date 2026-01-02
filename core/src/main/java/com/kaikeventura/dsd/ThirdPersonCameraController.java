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
    private float distanceFromPlayer = 7.0f;
    private float angleAroundPlayer = 180f;
    private float anglePitch = 15f;

    // Variáveis de Suavização (Lerp)
    private final Vector3 targetCameraPosition = new Vector3();
    private final Vector3 playerPosition = new Vector3();
    private final float smoothSpeed = 5.0f;
    private final float mouseSensitivity = 0.2f;

    // Controle de inicialização para evitar pulos
    private int framesToSkip = 5;

    public ThirdPersonCameraController(Camera camera, ModelInstance target) {
        this.camera = camera;
        this.target = target;

        // Calcula a posição inicial imediatamente
        calculateTargetPosition();

        // Força a câmera para a posição calculada sem suavização no primeiro frame
        camera.position.set(targetCameraPosition);

        // Garante que a câmera olhe para o jogador imediatamente
        target.transform.getTranslation(playerPosition);
        playerPosition.y += 1.0f;
        camera.lookAt(playerPosition);
        camera.update();
    }

    public void update(float delta) {
        // Ignora input nos primeiros frames para evitar saltos bruscos ao capturar o mouse
        if (framesToSkip > 0) {
            framesToSkip--;
            // Ainda precisamos atualizar a posição para seguir o jogador se ele se mover,
            // mas sem aplicar rotação do mouse.
            calculateTargetPosition();
            camera.position.lerp(targetCameraPosition, delta * smoothSpeed);
            target.transform.getTranslation(playerPosition);
            playerPosition.y += 1.0f;
            camera.lookAt(playerPosition);
            camera.update();
            return;
        }

        // 1. Processar input do mouse para rotação
        float deltaX = -Gdx.input.getDeltaX() * mouseSensitivity;
        float deltaY = -Gdx.input.getDeltaY() * mouseSensitivity;

        angleAroundPlayer += deltaX;
        anglePitch += deltaY;
        anglePitch = MathUtils.clamp(anglePitch, -10f, 60f);

        // 2. Calcular a posição ALVO da câmera
        calculateTargetPosition();

        // 3. Suavizar (Lerp) a posição atual da câmera em direção à posição alvo
        camera.position.lerp(targetCameraPosition, delta * smoothSpeed);

        // 4. Apontar a câmera para o jogador
        target.transform.getTranslation(playerPosition);
        // Adiciona um offset vertical para olhar para o peito/cabeça, não para os pés
        playerPosition.y += 1.0f;
        camera.lookAt(playerPosition);
        camera.update();
    }

    private void calculateTargetPosition() {
        // Calcular as distâncias horizontal e vertical
        float horizontalDistance = (float) (distanceFromPlayer * Math.cos(Math.toRadians(anglePitch)));
        float verticalDistance = (float) (distanceFromPlayer * Math.sin(Math.toRadians(anglePitch)));

        // Calcular a posição da câmera em coordenadas esféricas
        float theta = angleAroundPlayer;
        float offsetX = (float) (horizontalDistance * Math.sin(Math.toRadians(theta)));
        float offsetZ = (float) (horizontalDistance * Math.cos(Math.toRadians(theta)));

        target.transform.getTranslation(playerPosition);
        // Ajuste de altura do alvo (para a câmera orbitar em torno do centro do corpo, não dos pés)
        float targetHeightOffset = 1.0f;

        targetCameraPosition.x = playerPosition.x - offsetX;
        targetCameraPosition.z = playerPosition.z - offsetZ;
        targetCameraPosition.y = (playerPosition.y + targetHeightOffset) + verticalDistance;

        // Garantir que a câmera não entre no chão
        if (targetCameraPosition.y < 0.5f) {
            targetCameraPosition.y = 0.5f;
        }
    }
}
