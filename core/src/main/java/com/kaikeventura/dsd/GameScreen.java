package com.kaikeventura.dsd;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Vector3;

public class GameScreen implements Screen {

    // 3D Engine
    public PerspectiveCamera cam;
    public ModelBatch modelBatch;
    public Environment environment;

    // Modelos
    public Model playerModel, groundModel;
    public ModelInstance playerInstance, groundInstance;

    // PS1 Rendering
    FrameBuffer fbo;
    SpriteBatch spriteBatch;
    TextureRegion fboRegion;
    final int VIRTUAL_WIDTH = 320;
    final int VIRTUAL_HEIGHT = 240;

    // Lógica do Jogador
    Vector3 playerPosition = new Vector3(0, 1f, 0); // Começa um pouco acima do chão
    float speed = 5.0f; // Velocidade de movimento
    float rotateSpeed = 100f; // Velocidade de rotação

    @Override
    public void show() {
        // 1. ILUMINAÇÃO (Mais escura para parecer Dark Souls)
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.2f, 0.2f, 0.2f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        // 2. CÂMERA
        cam = new PerspectiveCamera(67, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        cam.near = 0.5f;
        cam.far = 100f;

        // 3. MODELOS (Chão e Player)
        ModelBuilder modelBuilder = new ModelBuilder();
        modelBatch = new ModelBatch();

        // O JOGADOR (Cubo Azul)
        playerModel = modelBuilder.createBox(1f, 2f, 1f, // Mais alto, parecendo um humano
            new Material(ColorAttribute.createDiffuse(Color.BLUE)),
            Usage.Position | Usage.Normal);
        playerInstance = new ModelInstance(playerModel);

        // O CHÃO (Plataforma Cinza Escura)
        groundModel = modelBuilder.createBox(50f, 1f, 50f, // Chão grande (50x50)
            new Material(ColorAttribute.createDiffuse(Color.DARK_GRAY)),
            Usage.Position | Usage.Normal);
        groundInstance = new ModelInstance(groundModel);
        groundInstance.transform.setToTranslation(0, -0.5f, 0); // Move para baixo dos pés do player

        // 4. SISTEMA DE PIXELS (PS1)
        spriteBatch = new SpriteBatch();
        fbo = new FrameBuffer(Pixmap.Format.RGB565, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, true);
    }

    @Override
    public void render(float delta) {
        // --- INPUTS (CONTROLE) ---
        // W e S movem para frente/trás
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            playerInstance.transform.translate(0, 0, speed * delta);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            playerInstance.transform.translate(0, 0, -speed * delta);
        }
        // A e D giram o personagem (Controle estilo "Tanque" clássico)
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            playerInstance.transform.rotate(Vector3.Y, rotateSpeed * delta);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            playerInstance.transform.rotate(Vector3.Y, -rotateSpeed * delta);
        }

        // --- ATUALIZA A CÂMERA ---
        // Pega a posição atual do jogador
        playerInstance.transform.getTranslation(playerPosition);

        // Coloca a câmera atrás e acima do jogador (Estilo 3ª Pessoa)
        // Offset: +0 no X, +3 no Y (altura), -4 no Z (distância atrás)
        // Nota: Esse cálculo é simples e a câmera não gira com o player ainda.
        cam.position.set(playerPosition.x, playerPosition.y + 3f, playerPosition.z - 4f);
        cam.lookAt(playerPosition); // Olha para o jogador
        cam.update();

        // --- RENDERIZAÇÃO NO FBO (TELA PEQUENA) ---
        fbo.begin();
        Gdx.gl.glViewport(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.05f, 1); // Fundo quase preto (Dark Souls)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(cam);
        modelBatch.render(groundInstance, environment); // Desenha chão
        modelBatch.render(playerInstance, environment); // Desenha player
        modelBatch.end();
        fbo.end();

        // --- DESENHA NA TELA GRANDE ---
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        fboRegion = new TextureRegion(fbo.getColorBufferTexture());
        fboRegion.flip(false, true);

        spriteBatch.begin();
        spriteBatch.draw(fboRegion, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        spriteBatch.end();
    }

    @Override
    public void resize(int width, int height) {
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        playerModel.dispose();
        groundModel.dispose();
        fbo.dispose();
        spriteBatch.dispose();
    }
}
