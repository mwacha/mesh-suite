package com.meshsuite.produto;

import com.meshsuite.auth.Action;
import com.meshsuite.auth.Module;
import com.meshsuite.auth.RequiresPermission;
import com.meshsuite.produto.dto.CategoriaRequest;
import com.meshsuite.produto.dto.CategoriaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final ProdutoRepository produtoRepository;

    public CategoriaService(CategoriaRepository categoriaRepository, ProdutoRepository produtoRepository) {
        this.categoriaRepository = categoriaRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<CategoriaResponse> listar(String busca, Boolean ativo, Pageable pageable) {
        Specification<Categoria> spec = Specification.allOf(
                CategoriaSpecifications.comBusca(busca),
                CategoriaSpecifications.comAtivo(ativo));
        Page<Categoria> pagina = categoriaRepository.findAll(spec, pageable);

        List<UUID> ids = pagina.getContent().stream().map(Categoria::getId).toList();
        Map<UUID, Long> contagens = ids.isEmpty()
                ? Map.of()
                : produtoRepository.countByCategoriaIdIn(ids).stream()
                        .collect(Collectors.toMap(
                                ProdutoRepository.CategoriaProdutoCount::getCategoriaId,
                                ProdutoRepository.CategoriaProdutoCount::getTotal));

        return pagina.map(categoria -> toResponse(categoria, contagens.getOrDefault(categoria.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public CategoriaResponse buscarPorId(UUID id) {
        return toResponse(buscarEntidadePorId(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public CategoriaResponse criar(UUID tenantId, CategoriaRequest request) {
        validarNome(request.nome(), null);

        Categoria categoria = new Categoria();
        categoria.setTenantId(tenantId);
        aplicar(categoria, request);
        return toResponse(categoriaRepository.saveAndFlush(categoria));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public CategoriaResponse atualizar(UUID id, CategoriaRequest request) {
        validarNome(request.nome(), id);

        Categoria categoria = buscarEntidadePorId(id);
        aplicar(categoria, request);
        return toResponse(categoriaRepository.saveAndFlush(categoria));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void excluir(UUID id) {
        Categoria categoria = buscarEntidadePorId(id);
        long vinculados = produtoRepository.countByCategoriaId(id);
        if (vinculados > 0) {
            throw new CategoriaEmUsoException(vinculados);
        }
        categoriaRepository.delete(categoria);
    }

    private Categoria buscarEntidadePorId(UUID id) {
        return categoriaRepository.findById(id).orElseThrow(CategoriaNaoEncontradaException::new);
    }

    private void validarNome(String nome, UUID idAtual) {
        boolean duplicado = idAtual == null
                ? categoriaRepository.existsByNome(nome)
                : categoriaRepository.existsByNomeAndIdNot(nome, idAtual);
        if (duplicado) {
            throw new CategoriaNomeDuplicadoException();
        }
    }

    private void aplicar(Categoria categoria, CategoriaRequest request) {
        categoria.setNome(request.nome());
        categoria.setDescricao(request.descricao());
        categoria.setAtivo(request.ativo() != null ? request.ativo() : true);
    }

    private CategoriaResponse toResponse(Categoria categoria) {
        return toResponse(categoria, produtoRepository.countByCategoriaId(categoria.getId()));
    }

    private CategoriaResponse toResponse(Categoria categoria, long produtosVinculados) {
        return new CategoriaResponse(
                categoria.getId(), categoria.getNome(), categoria.getDescricao(), categoria.getAtivo(),
                produtosVinculados, categoria.getCriadoEm());
    }
}
