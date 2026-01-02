package com.kaikeventura.dsd;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;

public class PlayerController {

    private final btRigidBody playerBody;
    private final Camera camera;
    private final Vector3 walkDirection = new Vector3();
    private final Vector3 tempVector = new Vector3();

    // Configurações de Movimento
    private final float speed = 5.0f;
    private final float jumpForce = 7.0f;
    private final float jumpForwardImpulse = 5.0f; // Força extra para frente ao pular correndo

    // Ajustes de Controle Aéreo
    private final float airControlForce = 10.0f;   // Reduzido para evitar "voar"
    private final float maxAirSpeed = 6.0f;        // Velocidade máxima horizontal permitida no ar

    public PlayerController(btRigidBody playerBody, Camera camera) {
        this.playerBody = playerBody;
        this.camera = camera;
    }

    public void update(float delta) {
        // --- ROTAÇÃO ---
        Vector3 direction = tempVector.set(camera.direction).set(camera.direction.x, 0, camera.direction.z).nor();
        float angle = new Vector2(direction.x, direction.z).angleDeg();

        Matrix4 transform = playerBody.getWorldTransform();
        Vector3 position = transform.getTranslation(new Vector3());
        transform.setToRotation(Vector3.Y, -angle - 90f);
        transform.setTranslation(position);
        playerBody.setWorldTransform(transform);

        // --- INPUT DE MOVIMENTO ---
        walkDirection.set(0, 0, 0);

        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            walkDirection.add(direction);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            walkDirection.sub(direction);
        }

        Vector3 strafeDirection = new Vector3(direction).crs(camera.up).nor();

        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            walkDirection.add(strafeDirection);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            walkDirection.sub(strafeDirection);
        }

        // Normaliza o vetor de input
        if (walkDirection.len2() > 0) {
            walkDirection.nor();
        }

        // --- FÍSICA E PULO ---
        boolean onGround = Math.abs(playerBody.getLinearVelocity().y) < 0.1f;

        if (onGround) {
            // NO CHÃO: Controle total
            if (walkDirection.len2() > 0) {
                Vector3 targetVelocity = walkDirection.scl(speed);
                playerBody.setLinearVelocity(tempVector.set(targetVelocity.x, playerBody.getLinearVelocity().y, targetVelocity.z));
            } else {
                playerBody.setLinearVelocity(tempVector.set(0, playerBody.getLinearVelocity().y, 0));
            }

            // PULO
            if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
                Vector3 jumpImpulse = new Vector3(0, jumpForce, 0);

                if (walkDirection.len2() > 0) {
                    // Normaliza de novo pois o scl acima alterou o vetor
                    Vector3 forwardBoost = new Vector3(walkDirection).nor().scl(jumpForwardImpulse);
                    jumpImpulse.add(forwardBoost);
                }

                playerBody.applyCentralImpulse(jumpImpulse);
            }

        } else {
            // NO AR: Controle limitado

            if (walkDirection.len2() > 0) {
                Vector3 velocity = playerBody.getLinearVelocity();
                float horizontalSpeed = new Vector2(velocity.x, velocity.z).len();

                // Só aplica força se estiver abaixo da velocidade máxima aérea
                if (horizontalSpeed < maxAirSpeed) {
                    Vector3 airForce = new Vector3(walkDirection).nor().scl(airControlForce * delta);
                    playerBody.applyCentralImpulse(airForce);
                }
            }
        }
    }
}
