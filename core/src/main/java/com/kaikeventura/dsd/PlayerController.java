package com.kaikeventura.dsd;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;

public class PlayerController {

    private final btRigidBody playerBody;
    private final Camera camera;
    private final Vector3 walkDirection = new Vector3();
    private final Vector3 tempVector = new Vector3();
    private final float speed = 5.0f;
    private final float jumpForce = 7.0f;

    public PlayerController(btRigidBody playerBody, Camera camera) {
        this.playerBody = playerBody;
        this.camera = camera;
    }

    public void update(float delta) {
        // --- ROTAÇÃO ---
        // O jogador irá rotacionar para sempre olhar na mesma direção da câmera.
        Vector3 cameraDirection = tempVector.set(camera.direction).set(camera.direction.x, 0, camera.direction.z).nor();
        playerBody.getWorldTransform().setFromEulerAngles(0, (float)Math.atan2(cameraDirection.x, cameraDirection.z) * MathUtils.radiansToDegrees, 0);


        // --- MOVIMENTAÇÃO (Frente/Trás e Strafe) ---
        walkDirection.set(0, 0, 0); // Reseta o vetor de direção a cada quadro

        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            walkDirection.add(cameraDirection);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            walkDirection.sub(cameraDirection);
        }

        // Para o "strafe", pegamos o vetor "direita" da câmera (produto vetorial da direção com o vetor "para cima")
        Vector3 strafeDirection = tempVector.set(camera.direction).crs(camera.up).set(tempVector.x, 0, tempVector.z).nor();

        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            walkDirection.add(strafeDirection);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            walkDirection.sub(strafeDirection);
        }

        // Normaliza para não andar mais rápido na diagonal e aplica a velocidade
        walkDirection.nor();
        Vector3 newVelocity = walkDirection.scl(speed);

        // Pega a velocidade vertical atual (para não anular a gravidade)
        float currentVerticalVelocity = playerBody.getLinearVelocity().y;

        // Aplica a nova velocidade, mantendo a componente Y
        playerBody.setLinearVelocity(tempVector.set(newVelocity.x, currentVerticalVelocity, newVelocity.z));

        // --- PULO ---
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            // Uma forma simples de checar se está no chão é ver se a velocidade vertical é próxima de zero.
            // Uma forma mais robusta usaria Ray-casting, mas isso funciona para começar.
            if (Math.abs(playerBody.getLinearVelocity().y) < 0.1f) {
                playerBody.applyCentralImpulse(tempVector.set(0, jumpForce, 0));
            }
        }
    }
}
